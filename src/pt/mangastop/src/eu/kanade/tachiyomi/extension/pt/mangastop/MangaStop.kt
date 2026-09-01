package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaStop : KeiSource() {

    private val apiUrl get() = "$baseUrl/wp-json/mangastop/v1"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.encodedPath.startsWith("/wp-json/") }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept", "application/json")

    override suspend fun getPopularManga(page: Int): MangasPage = paginated("$apiUrl/mais-populares", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = paginated("$apiUrl/recentes", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/busca".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("pagina", page.toString())
                .build()
            val result = client.get(url).parseAs<SearchDto>()

            return MangasPage(result.mangas, result.hasNextPage)
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue
        if (!genre.isNullOrBlank()) {
            val url = "$apiUrl/genero".toHttpUrl().newBuilder()
                .addQueryParameter("slug", genre)
                .addQueryParameter("pagina", page.toString())
                .addQueryParameter("por_pagina", PAGE_SIZE.toString())
                .build()

            return client.get(url).parseAs<ListDto>().toMangasPage()
        }

        return paginated(
            "$apiUrl/manga",
            page,
            type = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue,
        )
    }

    private suspend fun paginated(endpoint: String, page: Int, type: String? = null): MangasPage {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("por_pagina", PAGE_SIZE.toString())
            .apply { type?.takeIf(String::isNotBlank)?.let { addQueryParameter("tipo", it) } }
            .build()

        return client.get(url).parseAs<ListDto>().toMangasPage()
    }

    private fun ListDto.toMangasPage() = MangasPage(mangas, hasNextPage)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "obra") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = "/obra/$id" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$apiUrl/obra/${manga.url.substringAfterLast('/')}").parseAs<MangaDto>()

        return SMangaUpdate(manga = details.toSManga(), chapters = details.chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/leitor/${chapter.url}").parseAs<PagesDto>().toPageList()

    override fun getFilterList(data: JsonElement?) = FilterList(TypeFilter(), GenreFilter())

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/leitor/${chapter.url}"

    companion object {
        private const val PAGE_SIZE = 28
    }
}
