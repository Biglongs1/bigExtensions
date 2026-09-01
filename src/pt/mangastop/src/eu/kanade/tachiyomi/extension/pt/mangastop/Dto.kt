package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class PaginationDto(
    @SerialName("pagina_atual") val page: Int = 1,
    @SerialName("total_paginas") val totalPages: Int = 1,
    @SerialName("tem_proxima") val hasNextPage: Boolean = false,
)

/** `/manga`, `/recentes` and `/genero` share this envelope. */
@Serializable
class ListDto(
    @SerialName("mangas") private val entries: List<SeriesDto> = emptyList(),
    @SerialName("paginacao") private val pagination: PaginationDto? = null,
    // `/mais-populares` reports the page outside the pagination object.
    @SerialName("pagina") private val page: Int = 1,
    @SerialName("total_paginas") private val totalPages: Int = 1,
) {
    val mangas get() = entries.map(SeriesDto::toSManga)

    val hasNextPage get() = pagination?.hasNextPage ?: (page < totalPages)
}

@Serializable
class SearchDto(
    @SerialName("obras") private val works: SearchWorksDto = SearchWorksDto(),
    @SerialName("pagina") private val page: Int = 1,
) {
    val mangas get() = works.list.map(SeriesDto::toSManga)

    val hasNextPage get() = page < works.totalPages
}

@Serializable
class SearchWorksDto(
    @SerialName("lista") val list: List<SeriesDto> = emptyList(),
    @SerialName("total_paginas") val totalPages: Int = 1,
)

@Serializable
class SeriesDto(
    private val id: Int,
    private val titulo: String,
    private val thumbnail: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$id"
        title = titulo
        thumbnail_url = thumbnail?.takeIf(String::isNotBlank)
    }
}

@Serializable
class MangaDto(
    private val id: Int,
    private val titulo: String,
    private val alternativo: String? = null,
    private val sinopse: String? = null,
    @SerialName("capa_url") private val cover: String? = null,
    private val status: String? = null,
    private val autor: String? = null,
    private val artista: String? = null,
    private val tipo: String? = null,
    private val generos: List<GenreDto> = emptyList(),
    @SerialName("capitulos") private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$id"
        title = titulo
        thumbnail_url = cover?.takeIf(String::isNotBlank)
        author = autor?.takeIf(String::isNotBlank)
        artist = artista?.takeIf(String::isNotBlank)
        genre = (listOfNotNull(tipo?.takeIf(String::isNotBlank)) + generos.map(GenreDto::nome))
            .distinct()
            .joinToString()
        description = buildString {
            sinopse?.let { append(it.stripHtml()) }
            alternativo?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos: $it")
            }
        }.takeIf(String::isNotBlank)
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing", "em andamento", "em lançamento" -> SManga.ONGOING
            "completed", "completo" -> SManga.COMPLETED
            "hiatus", "hiato" -> SManga.ON_HIATUS
            "cancelled", "canceled", "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    val chapterList get() = chapters.map(ChapterDto::toSChapter)
}

@Serializable
class GenreDto(
    val nome: String,
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val numero: String? = null,
    @SerialName("data_publicacao") private val date: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = "Capítulo ${numero.orEmpty().ifBlank { "?" }}"
        chapter_number = numero?.toFloatOrNull() ?: -1f
        date_upload = DATE_FORMAT.tryParseDate(date)
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    }
}

@Serializable
class PagesDto(
    @SerialName("imagens") private val images: List<ImageDto> = emptyList(),
) {
    fun toPageList() = images.mapIndexed { index, image -> Page(index, imageUrl = image.url) }
}

@Serializable
class ImageDto(
    val url: String,
)

private val HTML_TAG_REGEX = Regex("<[^>]+>")

private fun String.stripHtml(): String = replace("<br />", "\n")
    .replace("<br>", "\n")
    .replace("</p>", "\n")
    .replace(HTML_TAG_REGEX, "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#039;", "'")
    .lines()
    .joinToString("\n") { it.trim() }
    .trim()
