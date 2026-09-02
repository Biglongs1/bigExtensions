package eu.kanade.tachiyomi.extension.en.mangaball

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaListResponseDto(
    private val data: List<MangaListItemDto>,
    private val pagination: PaginationDto,
) {
    fun toMangasPage(baseUrl: String) = MangasPage(
        mangas = data.map { it.toSManga(baseUrl) },
        hasNextPage = pagination.currentPage < pagination.lastPage && data.isNotEmpty(),
    )
}

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class MangaListItemDto(
    @SerialName("_id") private val id: String,
    private val name: String,
    private val cover: String,
    private val url: String,
    private val status: String? = null,
    private val tags: String? = null,
    private val authors: String? = null,
    private val isAdult: Boolean = false,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        val webUrl = this@MangaListItemDto.url.toHttpUrl()
        val webSegment = webUrl.pathSegments.getOrNull(1)
            ?: error("Manga URL is missing its slug")
        val slug = webSegment.removeSuffix("-$id").takeIf(String::isNotEmpty)
            ?: error("Manga URL is missing its slug")

        url = MangaKey(slug, id).serialized
        title = name
        thumbnail_url = cover.toAbsoluteHttpsUrl(baseUrl)
        author = authors.toFragmentTexts(baseUrl, "[data-person-id]").joinToString().takeIf(String::isNotEmpty)
        status = this@MangaListItemDto.status.toMangaStatus(baseUrl)
        genre = buildList {
            addAll(tags.toFragmentTexts(baseUrl, "[data-tag-id]"))
            if (isAdult) add("Adult")
        }.distinct().joinToString().takeIf(String::isNotEmpty)
    }
}

@Serializable
class TaxonomyResponseDto(
    val data: TaxonomyDto,
)

@Serializable
class TaxonomyDto(
    val content: List<TaxonomyItemDto>,
    val format: List<TaxonomyItemDto>,
    val genre: List<TaxonomyItemDto>,
    val origin: List<TaxonomyItemDto>,
    val theme: List<TaxonomyItemDto>,
)

@Serializable
class TaxonomyItemDto(
    @SerialName("_id") val id: String,
    val name: String,
)

@Serializable
class ChapterListResponseDto(
    @SerialName("ALL_CHAPTERS") private val allChapters: List<ChapterDto>,
) {
    fun toSChapterList(): List<SChapter> = allChapters
        .sortedByDescending(ChapterDto::number)
        .flatMap { chapter ->
            chapter.translations
                .filter { it.language == "en" }
                .sortedWith(
                    compareByDescending<ChapterTranslationDto>(ChapterTranslationDto::sortDate)
                        .thenByDescending(ChapterTranslationDto::sortId),
                )
                .map { it.toSChapter(chapter.number) }
        }
}

@Serializable
class ChapterDto(
    @SerialName("number_float") val number: Double,
    val translations: List<ChapterTranslationDto>,
)

@Serializable
class ChapterTranslationDto(
    private val id: String,
    private val name: String,
    val language: String,
    private val group: ChapterGroupDto? = null,
    private val date: String? = null,
    private val volume: JsonElement? = null,
) {
    fun toSChapter(number: Double) = SChapter.create().apply {
        url = id
        name = displayName
        chapter_number = number.toFloat()
        scanlator = group?.name
        date_upload = sortDate
    }

    private val displayName: String
        get() {
            val volumeNumber = volume?.jsonPrimitive?.contentOrNull?.takeUnless { it.isEmpty() || it == "0" }
            return if (volumeNumber != null && !name.contains("Vol.", ignoreCase = true)) {
                "Vol. $volumeNumber $name"
            } else {
                name
            }
        }

    val sortDate: Long
        get() = date.toEpochMillis()
    val sortId: String
        get() = id
}

@Serializable
class ChapterGroupDto(
    val name: String,
)

internal class MangaKey(
    val slug: String,
    val id: String,
) {
    val serialized get() = "$slug/$id"
}

internal fun String.toMangaKey(): MangaKey {
    val separator = lastIndexOf('/')
    require(separator in 1 until lastIndex) { "Invalid stored manga URL" }
    return MangaKey(substring(0, separator), substring(separator + 1))
}

internal fun String.toAbsoluteHttpsUrl(baseUrl: String): String {
    val url = toHttpUrlOrNull() ?: baseUrl.toHttpUrl().resolve(this)
        ?: error("Invalid URL: $this")
    return url.newBuilder().scheme("https").build().toString()
}

internal fun String?.toMangaStatus(baseUrl: String): Int {
    val normalized = this
        ?.let { Jsoup.parseBodyFragment(it, baseUrl).text() }
        ?.lowercase()
        .orEmpty()

    return when {
        "ongoing" in normalized -> SManga.ONGOING
        "completed" in normalized -> SManga.COMPLETED
        "hiatus" in normalized || "on-hold" in normalized || "on hold" in normalized -> SManga.ON_HIATUS
        "cancelled" in normalized -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

private fun String?.toFragmentTexts(baseUrl: String, selector: String): List<String> = this
    ?.let { Jsoup.parseBodyFragment(it, baseUrl) }
    ?.select(selector)
    ?.map { it.text() }
    ?.filter(String::isNotEmpty)
    ?.distinct()
    .orEmpty()

private fun String?.toEpochMillis(): Long {
    val value = this ?: return 0L
    Instant.parseOrNull(value.replace(' ', 'T'))?.let { return it.toEpochMilliseconds() }

    return runCatching {
        LocalDateTime.parse(value, DATE_FORMAT)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrDefault(0L)
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
