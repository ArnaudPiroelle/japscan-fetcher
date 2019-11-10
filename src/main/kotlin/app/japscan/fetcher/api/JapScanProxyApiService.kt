package app.japscan.fetcher.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface JapScanProxyApiService {
    @GET("/mangas/")
    suspend fun findMangas(): List<JapScanManga>

    @GET("/mangas/{mangaAlias}")
    suspend fun findDetails(@Path("mangaAlias") mangaAlias: String): JapScanDetails

    @GET("/mangas/{mangaAlias}/chapters")
    suspend fun findChapters(@Path("mangaAlias") mangaAlias: String): List<JapScanChapter>

    @GET("/mangas/{mangaAlias}/chapters/{chapterNumber}")
    suspend fun findPages(@Path("mangaAlias") mangaAlias: String, @Path("chapterNumber") chapterNumber: String): JapScanPages

    @GET
    suspend fun findPage(@Url url: String): ResponseBody
}