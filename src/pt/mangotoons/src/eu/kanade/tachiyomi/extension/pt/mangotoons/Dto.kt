package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ListDto(
    private val obras: List<WorkDto> = emptyList(),
    private val pagination: PaginationDto = PaginationDto(),
) {
    val mangas get() = obras.map(WorkDto::toSManga)

    val hasNextPage get() = pagination.hasNextPage
}

@Serializable
class PaginationDto(
    val hasNextPage: Boolean = false,
)

@Serializable
class WorkWrapperDto(
    val obra: WorkDto,
)

@Serializable
class WorkDto(
    private val id: Int,
    private val nome: String,
    private val imagem: String? = null,
    private val descricao: String? = null,
    @SerialName("titulo_alternativo") private val alternativeTitle: String? = null,
    @SerialName("status_nome") private val statusName: String? = null,
    @SerialName("formato_nome") private val formatName: String? = null,
    private val tags: List<TagDto> = emptyList(),
    private val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$id"
        title = nome
        thumbnail_url = imagem?.takeIf(String::isNotBlank)
        description = buildString {
            descricao?.takeIf(String::isNotBlank)?.let(::append)
            alternativeTitle?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Título alternativo: $it")
            }
        }.takeIf(String::isNotBlank)
        genre = (listOfNotNull(formatName?.takeIf(String::isNotBlank)) + tags.map(TagDto::nome))
            .distinct()
            .joinToString()
        status = when (statusName?.lowercase()) {
            "em andamento", "ativo" -> SManga.ONGOING
            "completo", "concluído", "concluido", "finalizado" -> SManga.COMPLETED
            "hiato", "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    val chapterList get() = capitulos.map { it.toSChapter(id) }
}

@Serializable
class TagDto(
    val nome: String,
)

@Serializable
class ChapterListDto(
    val capitulos: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val nome: String? = null,
    private val numero: Float = -1f,
    private val paywall: Boolean = false,
    @SerialName("criado_em") private val createdAt: String? = null,
) {
    fun toSChapter(workId: Int) = SChapter.create().apply {
        url = "/obra/$workId/capitulo/$id"
        name = buildString {
            append(nome?.takeIf(String::isNotBlank) ?: "Capítulo ${numero.toString().removeSuffix(".0")}")
            if (paywall) append(" \uD83D\uDD12")
        }
        chapter_number = numero
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterDetailDto(
    private val capitulo: ChapterPagesDto = ChapterPagesDto(),
) {
    val isLocked get() = capitulo.paywall

    fun toPageList() = capitulo.paginas.mapIndexed { index, page -> Page(index, imageUrl = page.url) }
}

@Serializable
class ChapterPagesDto(
    val paginas: List<PageDto> = emptyList(),
    val paywall: Boolean = false,
)

@Serializable
class PageDto(
    val url: String,
)

@Serializable
class FormatListDto(
    val formatos: List<OptionDto> = emptyList(),
)

@Serializable
class StatusListDto(
    val status: List<OptionDto> = emptyList(),
)

@Serializable
class OptionDto(
    val id: Int,
    val nome: String,
)
