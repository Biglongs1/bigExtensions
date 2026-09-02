package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

private const val CDN_URL = "https://cdn.atsu.moe/static"

@Serializable
class HomeResponse(
    private val items: List<MangaCardDto>,
) {
    fun toSMangaList() = items.map(MangaCardDto::toSManga)

    fun hasNextPage(pageSize: Int) = items.size == pageSize
}

@Serializable
class MangaCardDto(
    private val id: String,
    private val title: String,
    private val image: String? = null,
    private val smallImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@MangaCardDto.title
        thumbnail_url = (smallImage ?: image)?.takeIf(String::isNotEmpty)?.toImageUrl()
    }
}

@Serializable
class TypesenseSearchResponse(
    private val found: Int,
    private val hits: List<TypesenseHitDto>,
) {
    fun toSMangaList() = hits.map(TypesenseHitDto::toSManga)

    fun hasNextPage(page: Int, pageSize: Int) = page * pageSize < found
}

@Serializable
class TypesenseHitDto(
    private val document: TypesenseDocumentDto,
) {
    fun toSManga() = document.toSManga()
}

@Serializable
class TypesenseDocumentDto(
    private val id: String,
    private val title: String,
    private val poster: String? = null,
    private val posterSmall: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@TypesenseDocumentDto.title
        thumbnail_url = (posterSmall ?: poster)?.takeIf(String::isNotEmpty)?.toImageUrl()
    }
}

@Serializable
class MangaPageResponse(
    val mangaPage: MangaPageDto,
)

@Serializable
class MangaPageDto(
    private val id: String,
    private val title: String,
    private val englishTitle: String? = null,
    private val synopsis: String? = null,
    private val otherNames: List<String> = emptyList(),
    private val authors: List<AuthorDto> = emptyList(),
    private val genres: List<TermDto> = emptyList(),
    private val tags: List<TermDto> = emptyList(),
    private val poster: PosterDto? = null,
    private val released: Long? = null,
    private val status: String? = null,
    private val isAdult: Boolean,
    private val type: String,
    private val medium: String,
    private val scanlators: List<ScanlatorDto> = emptyList(),
) {
    fun isComic() = medium == "Comic"

    fun scanlatorNames() = scanlators.associate(ScanlatorDto::toPair)

    fun toSManga() = SManga.create().apply {
        url = id
        title = this@MangaPageDto.title
        author = authors
            .filter { it.isType("Author") }
            .map(AuthorDto::name)
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString()
        artist = authors
            .filter { it.isType("Artist") }
            .map(AuthorDto::name)
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString()
        description = buildDescription()
        genre = buildList {
            add(this@MangaPageDto.type)
            addAll(genres.map(TermDto::name))
            addAll(tags.map(TermDto::name))
            if (isAdult) add("Adult")
        }.distinct().joinToString()
        status = when (this@MangaPageDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "canceled", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = poster?.imageUrl()
    }

    private fun buildDescription(): String? = buildList {
        synopsis?.takeIf(String::isNotEmpty)?.let(::add)

        val alternativeTitles = buildList {
            englishTitle?.takeIf(String::isNotEmpty)?.let(::add)
            addAll(otherNames.filter(String::isNotEmpty))
        }.filterNot { it == title }.distinct()
        if (alternativeTitles.isNotEmpty()) {
            add("Alternative titles:\n${alternativeTitles.joinToString("\n")}")
        }

        released?.takeIf { it > 0L }?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            add("Released: $date")
        }
    }.takeIf(List<String>::isNotEmpty)?.joinToString("\n\n")
}

@Serializable
class AuthorDto(
    private val name: String,
    private val type: String,
) {
    fun name() = name

    fun isType(type: String) = this.type == type
}

@Serializable
class TermDto(
    private val name: String,
) {
    fun name() = name
}

@Serializable
class PosterDto(
    private val image: String? = null,
    private val smallImage: String? = null,
) {
    fun imageUrl() = (smallImage ?: image)?.takeIf(String::isNotEmpty)?.toImageUrl()
}

@Serializable
class ScanlatorDto(
    private val id: String,
    private val name: String,
) {
    fun toPair() = id to name
}

@Serializable
class AllChaptersResponse(
    private val chapters: List<ChapterDto>,
) {
    fun toSChapterList(
        mangaId: String,
        scanlators: Map<String, String>,
    ) = chapters
        .sortedWith(ChapterDto.descendingComparator)
        .map { it.toSChapter(mangaId, scanlators) }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String,
    private val number: Double,
    private val scanlationMangaId: String,
    private val createdAt: Long,
) {
    fun toSChapter(
        mangaId: String,
        scanlators: Map<String, String>,
    ) = SChapter.create().apply {
        url = "$mangaId/${this@ChapterDto.id}"
        name = this@ChapterDto.title
        chapter_number = number.toFloat()
        date_upload = createdAt
        scanlator = scanlators[scanlationMangaId]
    }

    companion object {
        val descendingComparator = compareByDescending<ChapterDto> { it.number }
            .thenByDescending { it.createdAt }
    }
}

@Serializable
class ReaderResponse(
    private val readChapter: ReaderChapterDto? = null,
) {
    fun toPageList() = readChapter?.toPageList().orEmpty()
}

@Serializable
class ReaderChapterDto(
    private val pages: List<ReaderPageDto>,
) {
    fun toPageList() = pages
        .sortedBy(ReaderPageDto::number)
        .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl()) }
}

@Serializable
class ReaderPageDto(
    private val image: String,
    private val number: Int,
) {
    fun number() = number

    fun imageUrl() = image.toImageUrl()
}

@Serializable
class AvailableFiltersDto(
    private val genres: List<FilterOptionDto>,
    private val tags: List<FilterOptionDto>,
    private val types: List<FilterOptionDto>,
    private val statuses: List<FilterOptionDto>,
) {
    fun genreOptions() = genres.toOptions()

    fun tagOptions() = tags.toOptions(showGroup = true)

    fun typeOptions() = types.toOptions()

    fun statusOptions() = statuses.toOptions()

    private fun List<FilterOptionDto>.toOptions(showGroup: Boolean = false) = map {
        it.toPair(showGroup)
    }.sortedBy { it.first }
}

@Serializable
class FilterOptionDto(
    private val id: String,
    private val name: String,
    private val group: String? = null,
) {
    fun toPair(showGroup: Boolean) = if (showGroup && group != null) {
        "$name ($group)" to id
    } else {
        name to id
    }
}

private fun String.toImageUrl(): String {
    if (startsWith("http://") || startsWith("https://")) return this

    val path = removePrefix("/").removePrefix("static/")
    return "$CDN_URL/$path"
}
