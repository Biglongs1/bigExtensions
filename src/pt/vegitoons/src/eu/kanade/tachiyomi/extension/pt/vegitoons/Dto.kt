package eu.kanade.tachiyomi.extension.pt.vegitoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class MangaListDto(val obras: List<MangaDto>, val totalPaginas: Int)

@Serializable
class MangaDto(
    @SerialName("obr_id") private val id: Int,
    @SerialName("obr_nome") private val title: String,
    @SerialName("obr_imagem") private val cover: String? = null,
    @SerialName("obr_descricao") private val description: String? = null,
    private val status: StatusDto? = null,
    private val tags: List<TagDto> = emptyList(),
    val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$id"
        title = this@MangaDto.title
        thumbnail_url = cover
        description = this@MangaDto.description?.let { Jsoup.parseBodyFragment(it).text() }
        genre = tags.joinToString { it.name }
        status = when (this@MangaDto.status?.name?.lowercase()) {
            "em andamento", "ativo" -> SManga.ONGOING
            "concluído", "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class StatusDto(@SerialName("stt_nome") val name: String)

@Serializable
class TagDto(@SerialName("tag_nome") val name: String)

@Serializable
class ChapterDto(
    @SerialName("cap_id") private val id: Int,
    @SerialName("cap_nome") private val title: String,
    @SerialName("cap_numero") private val number: Float,
    @SerialName("cap_criado_em") private val createdAt: String? = null,
    @SerialName("cap_liberado") private val available: Boolean = true,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/capitulo/$id"
        name = if (available) title else "🔒 $title"
        chapter_number = number
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesDto(@SerialName("cap_paginas") val pages: List<String>)

@Serializable
class FilterDataDto(val formatos: List<FilterOptionDto>, val generos: List<FilterOptionDto>, val status: List<FilterOptionDto>, val tags: List<FilterOptionDto>)

@Serializable
class FilterOptionDto(
    @JsonNames("formt_id", "gen_id", "stt_id", "tag_id") val id: Int,
    @JsonNames("formt_nome", "gen_nome", "stt_nome", "tag_nome") val name: String,
)
