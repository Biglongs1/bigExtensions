package eu.kanade.tachiyomi.extension.pt.loverstoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val data: List<MangaDto> = emptyList(),
    val totalPages: Int = 1,
)

@Serializable
class MangaDto(
    private val id: String,
    private val slug: String? = null,
    private val title: String,
    private val description: String? = null,
    @SerialName("coverImage") private val cover: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val studio: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val type: String? = null,
    @SerialName("customBadge") private val badge: String? = null,
    val lastChapters: List<ChapterDto> = emptyList(),
) {
    val isNovel get() = type == "novel"

    val scanlator get() = badge?.takeIf(String::isNotBlank)

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug?.takeIf(String::isNotBlank) ?: id
        title = this@MangaDto.title
        thumbnail_url = cover?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http")) it else baseUrl + it }
        author = listOfNotNull(this@MangaDto.author, studio).firstOrNull(String::isNotBlank)
        artist = this@MangaDto.artist?.takeIf(String::isNotBlank)
        genre = genres.joinToString()
        description = this@MangaDto.description?.trim()
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String? = null,
    private val title: String? = null,
    @SerialName("createdAt") private val createdAt: String? = null,
) {
    fun toSChapter(scanlator: String?) = SChapter.create().apply {
        url = id
        name = title?.takeIf(String::isNotBlank) ?: "Capítulo ${number.orEmpty()}"
        chapter_number = number?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(createdAt)
        this.scanlator = scanlator
    }
}

@Serializable
class ChapterPagesDto(
    private val images: List<String>? = null,
) {
    fun toPageList(baseUrl: String) = images.orEmpty().mapIndexed { index, image ->
        Page(index, imageUrl = if (image.startsWith("http")) image else baseUrl + image)
    }
}

@Serializable
class ScanDto(
    val id: String,
    val name: String,
)

@Serializable
class FilterData(
    val scans: List<ScanDto> = emptyList(),
)
