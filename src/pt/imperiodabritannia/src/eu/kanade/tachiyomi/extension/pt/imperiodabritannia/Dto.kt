package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class RankingDto(val obras: List<MangaDto>)

@Serializable
class MangaListDto(val obras: List<MangaDto>, val pagination: PaginationDto)

@Serializable
class PaginationDto(val hasNextPage: Boolean)

@Serializable
class MangaDetailsDto(val obra: MangaDto)

@Serializable
class MangaDto(
    val id: Int,
    @JsonNames("title") private val nome: String,
    @JsonNames("coverImage") private val imagem: String? = null,
    private val descricao: String? = null,
    @SerialName("status_nome") private val status: String? = null,
    private val tags: List<TagDto> = emptyList(),
    private val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$id"
        title = nome
        thumbnail_url = imagem?.let(::cdnUrl)
        description = descricao?.let { Jsoup.parseBodyFragment(it).text() }
        genre = tags.joinToString { it.nome }
        status = when (this@MangaDto.status?.lowercase()) {
            "ativo", "em andamento" -> SManga.ONGOING
            "concluído", "completo", "finalizado" -> SManga.COMPLETED
            "hiato", "em hiato" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    fun chapterList() = capitulos.map { it.toSChapter(id) }.sortedByDescending { it.chapter_number }
}

@Serializable
class TagDto(val nome: String)

@Serializable
class ChapterDto(
    private val numero: Float,
    private val nome: String? = null,
    @SerialName("criado_em") private val createdAt: String? = null,
    @SerialName("paywall_bloqueado") private val locked: Boolean = false,
) {
    fun toSChapter(mangaId: Int) = SChapter.create().apply {
        val number = numero.toString().removeSuffix(".0")
        url = "/obra/$mangaId/capitulo/$number"
        val title = nome?.takeIf(String::isNotBlank) ?: "Capítulo $number"
        name = if (locked) "🔒 $title" else title
        chapter_number = numero
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterDetailsDto(val capitulo: ChapterPagesDto)

@Serializable
class ChapterPagesDto(
    @SerialName("obra_id") private val mangaId: Int,
    private val numero: Float,
    private val paginas: List<PageDto> = emptyList(),
) {
    fun toPages(): List<Page> = paginas.sortedBy { it.numero }.mapIndexed { index, page ->
        val chapterNumber = numero.toString().removeSuffix(".0")
        val image = page.url ?: page.cdnId ?: "obras/$mangaId/capitulo-$chapterNumber/pagina_${page.numero.toString().padStart(3, '0')}.webp"
        Page(index, imageUrl = cdnUrl(image))
    }
}

@Serializable
class PageDto(
    val numero: Int,
    val url: String? = null,
    @SerialName("cdn_id") @JsonNames("cdnId") val cdnId: String? = null,
)

private fun cdnUrl(path: String): String = "https://cdn.imperiodabritannia.net/".toHttpUrl().resolve(path)!!.toString()

@Serializable
class FormatsDto(val formatos: List<FilterOptionDto>)

@Serializable
class TagsDto(val tags: List<FilterOptionDto>)

@Serializable
class FilterDataDto(val formats: List<FilterOptionDto>, val tags: List<FilterOptionDto>)

@Serializable
class FilterOptionDto(val id: Int, val nome: String)
