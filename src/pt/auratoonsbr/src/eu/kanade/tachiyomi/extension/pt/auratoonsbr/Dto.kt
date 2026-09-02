package eu.kanade.tachiyomi.extension.pt.auratoonsbr

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class CatalogRequestDto(
    val filters: CatalogFiltersDto,
    val toolbarQuery: String,
    val sortBy: String,
    val sortDir: String,
    val page: Int,
    val blockedGenrePt: List<String> = emptyList(),
)

@Serializable
class CatalogFiltersDto(
    val status: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val categoryMode: String = "or",
    val yearFrom: String = "",
    val yearTo: String = "",
    val chaptersMin: String = "",
    val chaptersMax: String = "",
    val adult: String = "Ocultar",
)

@Serializable
class CatalogResponseDto(
    private val items: List<MangaDto>,
    private val total: Int,
) {
    fun toMangasPage(page: Int, baseUrl: String) = MangasPage(
        mangas = items.map { it.toSManga(baseUrl) },
        hasNextPage = page * PAGE_SIZE < total,
    )

    companion object {
        private const val PAGE_SIZE = 24
    }
}

@Serializable
class MangaDetailDto(
    val manga: MangaDto,
    private val chapters: List<ChapterDto>,
    private val scanGroupName: String? = null,
) {
    fun toSChapterList(): List<SChapter> = chapters
        .filter(ChapterDto::isPublic)
        .sortedWith(ChapterDto.descendingComparator)
        .map { it.toSChapter(manga.slug, scanGroupName) }
}

@Serializable
class MangaDto(
    private val mangaDexId: String,
    private val title: String,
    private val image: String? = null,
    private val genre: String? = null,
    private val themes: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val rating: Double? = null,
    private val views: Long? = null,
    private val year: Int? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val alternativeTitles: List<String>? = null,
    private val adult: Boolean = false,
) {
    val slug get() = mangaDexId

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = mangaDexId
        title = this@MangaDto.title
        thumbnail_url = image?.toAbsoluteUrl(baseUrl)
        author = this@MangaDto.author.validMetadata()
        artist = this@MangaDto.artist.validMetadata()
        status = this@MangaDto.status.toMangaStatus()
        genre = buildList {
            type?.takeIf(String::isNotEmpty)?.let { add(it.replaceFirstChar(Char::uppercase)) }
            addAll(this@MangaDto.genre.toTerms())
            addAll(themes.toTerms())
            if (adult) add("Adulto")
        }.distinct().joinToString().takeIf(String::isNotEmpty)
        description = buildString {
            this@MangaDto.description?.takeIf(String::isNotEmpty)?.let(::append)
            alternativeTitles?.filter(String::isNotEmpty)?.takeIf(List<String>::isNotEmpty)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos:\n")
                append(it.joinToString("\n"))
            }
            year?.takeIf { it > 0 }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Ano: $it")
            }
            rating?.let {
                if (isNotEmpty()) append("\n")
                append("Nota: ${it.toString().removeSuffix(".0")}/5")
            }
            views?.takeIf { it > 0 }?.let {
                if (isNotEmpty()) append("\n")
                append("Visualizações: $it")
            }
        }.takeIf(String::isNotEmpty)
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val chapterNumber: String,
    private val volume: String? = null,
    private val title: String? = null,
    private val readableAt: String? = null,
    private val releaseStatus: String,
    private val accessLevel: String,
) {
    fun isPublic() = releaseStatus == "published" && accessLevel == "public"

    fun toSChapter(mangaSlug: String, group: String?) = SChapter.create().apply {
        url = "$mangaSlug/${this@ChapterDto.id}"
        name = buildString {
            volume?.takeIf(String::isNotEmpty)?.let { append("Vol. $it ") }
            append("Capítulo $chapterNumber")
            title?.takeIf(String::isNotEmpty)?.let { append(" - $it") }
        }
        chapter_number = numericChapter
        scanlator = group
        date_upload = Instant.parseOrNull(readableAt.orEmpty())?.toEpochMilliseconds() ?: 0L
    }

    private val numericChapter get() = chapterNumber.toFloatOrNull() ?: -1F
    private val date get() = Instant.parseOrNull(readableAt.orEmpty())?.toEpochMilliseconds() ?: 0L

    companion object {
        val descendingComparator = compareByDescending<ChapterDto> { it.numericChapter }
            .thenByDescending { it.date }
            .thenByDescending { it.id.toLongOrNull() ?: 0L }
    }
}

@Serializable
class ChapterPagesDto(
    private val ok: Boolean,
    private val urls: List<String> = emptyList(),
) {
    fun toPageList(baseUrl: String): List<Page> = if (ok) {
        urls.mapIndexed { index, url -> Page(index, imageUrl = url.toAbsoluteUrl(baseUrl)) }
    } else {
        emptyList()
    }
}

@Serializable
class FilterDataDto(
    val genres: List<FilterOptionDto>,
    val themes: List<FilterOptionDto>,
)

@Serializable
class FilterOptionDto(
    val label: String,
    val value: String,
)

private fun String.toAbsoluteUrl(baseUrl: String): String = if (startsWith("http://") || startsWith("https://")) {
    this
} else {
    baseUrl.toHttpUrl().resolve(this)?.toString() ?: this
}

private fun String?.validMetadata(): String? = this?.takeUnless { it.isEmpty() || it == "N/A" || it == "—" }

private fun String?.toTerms(): List<String> = this
    ?.split(',')
    ?.map(String::trim)
    ?.filter { it.isNotEmpty() && it != "—" }
    .orEmpty()

private fun String?.toMangaStatus(): Int = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled", "canceled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
