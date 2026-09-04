package eu.kanade.tachiyomi.extension.pt.inkscan

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class GenresDto(val tags: List<String>? = null, val generos: List<String>? = null)

@Serializable
class MangaDto(
    private val id: String,
    private val titulo: String,
    @SerialName("capa_url") private val cover: String? = null,
    private val descricao: String? = null,
    private val status: String? = null,
    private val generos: List<String>? = null,
    private val tags: List<String>? = null,
    private val autor: String? = null,
    private val artista: String? = null,
    @SerialName("titulos_alternativos") private val alternativeTitles: List<String>? = null,
    @SerialName("pasta_s3") val folder: String? = null,
    @SerialName("is_acervo_b") private val adult: Boolean? = null,
) {
    val cdnUrl: String get() = if (adult == true) "https://inck2.inkscann.live" else "https://cdn.inkscann.live"

    fun toSManga() = SManga.create().apply {
        url = "/manga/$id"
        title = titulo
        thumbnail_url = cover
        description = buildString {
            descricao?.let { append(Jsoup.parseBodyFragment(it).text()) }
            alternativeTitles?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos: ${it.joinToString()}")
            }
        }
        author = autor
        artist = artista
        genre = (generos.orEmpty() + tags.orEmpty()).distinct().joinToString()
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing", "em andamento" -> SManga.ONGOING
            "completed", "completo", "concluído" -> SManga.COMPLETED
            "hiatus", "hiato" -> SManga.ON_HIATUS
            "cancelled", "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val numero: Float,
    private val titulo: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        val number = numero.toString().removeSuffix(".0")
        url = "/manga/$mangaId/chapter/$number#$id"
        name = titulo?.takeIf(String::isNotBlank) ?: "Capítulo $number"
        chapter_number = numero
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterRequestDto(private val id: String)

@Serializable
class ChapterPagesDto(
    private val arquivos: List<PageFileDto> = emptyList(),
    private val paginas: List<Int> = emptyList(),
    @SerialName("total_paginas") private val totalPages: Int = 0,
) {
    fun toPages(work: MangaDto, chapterNumber: String): List<Page> {
        val folder = work.folder ?: return emptyList()
        val filenames = if (arquivos.isNotEmpty()) {
            arquivos.sortedBy { it.ordem }.map { it.filename ?: "Pag_${it.ordem}.webp" }
        } else {
            (paginas.ifEmpty { (1..totalPages).toList() }).map { "Pag_$it.webp" }
        }
        val directory = "${work.cdnUrl}/$folder/Cap_$chapterNumber/".toHttpUrl()
        return filenames.mapIndexed { index, filename ->
            Page(index, imageUrl = directory.newBuilder().addPathSegment(filename).build().toString())
        }
    }
}

@Serializable
class PageFileDto(val ordem: Int, val filename: String? = null)

@Serializable
class SessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") private val expiresAt: Long,
) {
    fun isValid(): Boolean = expiresAt > System.currentTimeMillis() / 1000 + 60
}

@Serializable
class RefreshRequestDto(@SerialName("refresh_token") private val refreshToken: String)
