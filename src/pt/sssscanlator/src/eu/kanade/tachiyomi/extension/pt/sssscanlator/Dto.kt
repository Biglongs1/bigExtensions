package eu.kanade.tachiyomi.extension.pt.sssscanlator

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class CsrfDto(
    val csrfToken: String,
)

@Serializable
class LibraryDto(
    private val catalogo: List<SeriesDto> = emptyList(),
    private val pagination: PaginationDto = PaginationDto(),
) {
    val entries get() = catalogo.filterNot(SeriesDto::isDecoy).map(SeriesDto::toSManga)

    val hasNextPage get() = pagination.page < pagination.totalPages
}

@Serializable
class PaginationDto(
    val page: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    private val id: String = "",
    private val cover: String? = null,
) {
    // Every listing ships a decoy entry the site itself never renders.
    val isDecoy get() = id.startsWith("trap-")

    fun toSManga() = SManga.create().apply {
        url = "/obra/$slug"
        title = this@SeriesDto.title
        thumbnail_url = cover?.takeIf(String::isNotBlank)
    }
}

/** Title and cover live in a flight node separate from the rest of the details. */
@Serializable
class HeaderDto(
    val seriesId: String,
    val title: String,
    val cover: String? = null,
)

@Serializable
class DetailsDto(
    val slug: String,
    private val refId: String,
    private val chapterPayload: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val coverImage: String? = null,
    private val status: String? = null,
) {
    fun toSManga(header: HeaderDto?, genres: List<String>) = SManga.create().apply {
        url = "/obra/$slug"
        title = header?.title.orEmpty()
        thumbnail_url = coverImage?.takeIf(String::isNotBlank) ?: header?.cover
        description = this@DetailsDto.description?.takeIf(String::isNotBlank)
        author = this@DetailsDto.author?.takeIf(String::isNotBlank)
        artist = this@DetailsDto.artist?.takeIf(String::isNotBlank)
        genre = genres.joinToString()
        status = when (this@DetailsDto.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELED", "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    /**
     * The chapter list ships base64 + XOR-scrambled under a key built from the series id and slug.
     * The plaintext is percent-encoded so it survives the site's byte-wise string handling.
     */
    val chapterList: List<SChapter> get() {
        val payload = chapterPayload?.takeIf(String::isNotBlank) ?: return emptyList()
        val key = "YOMU_CH_${refId.reversed()}_$slug"
        val scrambled = Base64.decode(payload, Base64.DEFAULT)

        val encoded = buildString(scrambled.size) {
            scrambled.forEachIndexed { index, byte ->
                append(((byte.toInt() and 0xFF) xor key[index % key.length].code xor (index % 53)).toChar())
            }
        }

        // URLDecoder turns "+" into a space, decodeURIComponent does not.
        return URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
            .parseAs<List<ChapterDto>>()
            .map { it.toSChapter(slug) }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Double,
    private val title: String? = null,
    private val releaseDate: String? = null,
    private val releaseAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val label = number.toString().removeSuffix(".0")
        url = id
        name = title?.takeIf(String::isNotBlank) ?: "Capítulo $label"
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(releaseAt).takeIf { it != 0L } ?: DATE_FORMAT.tryParseDate(releaseDate)
        memo = buildJsonObject {
            put("slug", mangaSlug)
            put("number", label)
        }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}

@Serializable
class PagesDto(
    private val chapter: ChapterContentDto = ChapterContentDto(),
) {
    fun toPageList() = chapter.content.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
}

@Serializable
class ChapterContentDto(
    val content: List<String> = emptyList(),
)

@Serializable
class LockedDto(
    private val error: String? = null,
    private val lockedType: String? = null,
) {
    val message get() = when (lockedType) {
        "VIP", "VIP_PLUS" -> "Capítulo exclusivo para assinantes VIP."
        "TIME" -> "Capítulo ainda não foi lançado."
        else -> error ?: "Capítulo indisponível."
    }
}
