package eu.kanade.tachiyomi.extension.en.mangadot

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    @SerialName("manga_list") private val mangaList: List<MangaListItemDto>,
    private val pagination: PaginationDto,
) {
    fun toMangasPage(baseUrl: String) = MangasPage(
        mangas = mangaList.map { it.toSManga(baseUrl) },
        hasNextPage = pagination.currentPage < pagination.totalPages,
    )
}

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaListItemDto(
    private val id: Int,
    private val title: String,
    private val photo: String? = null,
    private val status: String? = null,
    @SerialName("country_of_origin") private val countryOfOrigin: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = this@MangaListItemDto.title
        thumbnail_url = photo?.toAbsoluteUrl(baseUrl)
        status = this@MangaListItemDto.status.toMangaStatus()
        genre = countryOfOrigin.toPublicationType()
    }
}

@Serializable
class MangaDetailResponseDto(
    private val manga: MangaDetailDto,
    @SerialName("total_chapters") private val totalChapters: Int,
) {
    fun toSManga(baseUrl: String) = manga.toSManga(baseUrl, totalChapters)
}

@Serializable
class MangaDetailDto(
    private val id: Int,
    private val title: String,
    private val description: String? = null,
    private val synopsis: String? = null,
    private val photo: String? = null,
    private val status: String? = null,
    private val authors: String? = null,
    private val artists: String? = null,
    @SerialName("alt_titles") private val altTitles: List<String>,
    private val genres: List<String>,
    private val tags: List<DetailTagDto>,
    @SerialName("avg_rating") private val averageRating: Double? = null,
    @SerialName("country_of_origin") private val countryOfOrigin: String? = null,
) {
    fun toSManga(baseUrl: String, totalChapters: Int) = SManga.create().apply {
        url = id.toString()
        title = this@MangaDetailDto.title
        thumbnail_url = photo?.toAbsoluteUrl(baseUrl)
        author = authors.decodeNames()
        artist = artists.decodeNames()
        status = this@MangaDetailDto.status.toMangaStatus()
        genre = buildList {
            countryOfOrigin.toPublicationType()?.let(::add)
            addAll(genres)
            addAll(tags.flatMap(DetailTagDto::names))
        }.distinct().joinToString()
        description = buildString {
            (this@MangaDetailDto.description ?: synopsis)?.takeIf(String::isNotEmpty)?.let(::append)
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                append(altTitles.joinToString("\n"))
            }
            averageRating?.let {
                if (isNotEmpty()) append("\n\n")
                append("Rating: ${it.toString().removeSuffix(".0")}/10")
            }
            if (totalChapters > 0) {
                if (isNotEmpty()) append("\n")
                append("Chapters: $totalChapters")
            }
        }.takeIf(String::isNotEmpty)
    }
}

@Serializable
class DetailTagDto(
    private val name: String? = null,
    private val tags: List<TagNameDto> = emptyList(),
) {
    fun names(): List<String> = listOfNotNull(name) + tags.map(TagNameDto::name)
}

@Serializable
class TagNameDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_number") private val chapterNumber: Double? = null,
    private val number: Double? = null,
    @SerialName("chapter_title") private val chapterTitle: String? = null,
    private val name: String? = null,
    private val title: String? = null,
    private val source: String? = null,
    @SerialName("source_type") private val sourceType: String? = null,
    private val scanlator: String? = null,
    @SerialName("scanlator_name") private val scanlatorName: String? = null,
    @SerialName("group_name") private val groupName: String? = null,
    @SerialName("date_added") private val dateAdded: String? = null,
    @SerialName("uploaded_at") private val uploadedAt: String? = null,
    private val date: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = buildString {
            append(id)
            if (actualSource == "user") append("?source=user")
        }
        name = actualTitle ?: "Chapter ${actualNumber.toString().removeSuffix(".0")}"
        chapter_number = actualNumber.toFloat()
        scanlator = this@ChapterDto.scanlator ?: scanlatorName ?: groupName
        date_upload = sortDate
    }

    private val actualNumber get() = chapterNumber ?: number ?: error("Chapter number is missing")
    private val actualSource get() = sourceType ?: source ?: error("Chapter source is missing")
    private val actualTitle get() = chapterTitle?.takeIf(String::isNotEmpty)
        ?: name?.takeIf(String::isNotEmpty)
        ?: title?.takeIf(String::isNotEmpty)

    val sortNumber get() = actualNumber
    val sortDate get() = Instant.parseOrNull((uploadedAt ?: date ?: dateAdded)?.replace(' ', 'T').orEmpty())
        ?.toEpochMilliseconds()
        ?: 0L
    val chapterId get() = id
}

fun List<ChapterDto>.toSChapterList(): List<SChapter> = sortedWith(
    compareByDescending<ChapterDto>(ChapterDto::sortNumber)
        .thenByDescending(ChapterDto::sortDate)
        .thenByDescending(ChapterDto::chapterId),
).map(ChapterDto::toSChapter)

@Serializable
class ChapterImagesDto(
    private val images: List<ImageDto>,
) {
    fun toPageList(baseUrl: String) = images.mapIndexed { index, image ->
        Page(index, imageUrl = image.url.toAbsoluteUrl(baseUrl))
    }
}

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class FacetResponseDto(
    private val facets: FacetsDto,
) {
    fun toFilterData(tagCatalog: TagCatalogDto) = FilterDataDto(
        genres = facets.genres.map(FacetValueDto::key).sorted(),
        statuses = facets.status.map(FacetValueDto::key),
        origins = facets.origin.map(FacetValueDto::key),
        contentRatings = facets.contentRating.map(FacetValueDto::key),
        tags = tagCatalog.topTags(),
    )
}

@Serializable
class FacetsDto(
    val genres: List<FacetValueDto>,
    val status: List<FacetValueDto>,
    val origin: List<FacetValueDto>,
    @SerialName("content_rating") val contentRating: List<FacetValueDto>,
)

@Serializable
class FacetValueDto(
    val key: String,
)

@Serializable
class TagCatalogDto(
    private val categories: List<TagCategoryDto>,
) {
    fun topTags() = categories
        .flatMap(TagCategoryDto::tags)
        .filterNot(CatalogTagDto::isAdult)
        .distinctBy(CatalogTagDto::name)
        .sortedByDescending(CatalogTagDto::seriesCount)
        .take(MAX_TAG_FILTERS)
        .map(CatalogTagDto::name)
        .sorted()

    companion object {
        private const val MAX_TAG_FILTERS = 80
    }
}

@Serializable
class TagCategoryDto(
    val tags: List<CatalogTagDto>,
)

@Serializable
class CatalogTagDto(
    val name: String,
    @SerialName("series_count") val seriesCount: Int,
    @SerialName("is_adult") val isAdult: Boolean,
)

@Serializable
class FilterDataDto(
    val genres: List<String>,
    val statuses: List<String>,
    val origins: List<String>,
    val contentRatings: List<String>,
    val tags: List<String>,
)

private fun String?.decodeNames(): String? = this
    ?.takeIf(String::isNotEmpty)
    ?.parseAs<List<String>>()
    ?.joinToString()

private fun String.toAbsoluteUrl(baseUrl: String) = if (startsWith("http://") || startsWith("https://")) {
    this
} else {
    "$baseUrl/${removePrefix("/")}"
}

private fun String?.toMangaStatus(): Int = when (this?.lowercase()) {
    "ongoing", "releasing" -> SManga.ONGOING
    "completed", "finished" -> SManga.COMPLETED
    "hiatus", "on hiatus", "on_hiatus" -> SManga.ON_HIATUS
    "cancelled", "canceled", "dropped", "discontinued" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private fun String?.toPublicationType(): String? = when (this) {
    "JP", "Japan" -> "Manga"
    "KR" -> "Manhwa"
    "CN", "TW" -> "Manhua"
    else -> this?.takeIf(String::isNotEmpty)
}
