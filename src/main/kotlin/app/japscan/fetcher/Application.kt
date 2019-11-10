package app.japscan.fetcher

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.buffer
import okio.sink
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.lang.Exception
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


fun main() {
    val db = Database.connect("jdbc:h2:file:D:\\Downloads\\eBooks\\h2", driver = "org.h2.Driver")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Mangas, Chapters)
    }

    val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    val ebooksFolder = File("D:\\Downloads\\eBooks")

    transaction {
        val mangas = Mangas.selectAll().map { it.get(Mangas.name) }
        println("Existing mangas in the database: $mangas")
    }

    val logging = HttpLoggingInterceptor()
    logging.level = HttpLoggingInterceptor.Level.NONE
    val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl("https://japscan-proxy.herokuapp.com")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val proxyService = retrofit.create(JapScanProxyApiService::class.java)

    runBlocking {
        println("Get the manga list from japscan")
        val mangas = proxyService.findMangas()

        mangas.forEach { manga ->
            println("Fetch ${manga.name}")
            val mangaName = manga.name.replace("/", "-")
            val mangaFolder = File(ebooksFolder, mangaName)

            val chapters = proxyService.findChapters(manga.alias)
            transaction {
                val existingManga = Mangas.select { Mangas.alias eq manga.alias }.firstOrNull()
                if (existingManga == null) {
                    Mangas.insert {
                        it[alias] = manga.alias
                        it[name] = manga.name
                    }
                }

                chapters.forEach {chapter ->
                    val existingChapter = Chapters.select(where = { (Chapters.mangaAlias eq manga.alias) and (Chapters.number eq chapter.number) }).firstOrNull()
                    if (existingChapter == null){
                        Chapters.insert {
                            it[mangaAlias] = manga.alias
                            it[number] = chapter.number
                        }
                    }
                }
            }

            chapters.forEach { chapter ->
                val alreadyDownloaded = transaction {
                    Chapters.select(where = { (Chapters.mangaAlias eq manga.alias) and (Chapters.number eq chapter.number) }).firstOrNull()?.get(Chapters.downloaded) ?: false
                }

                val chapterName = "$mangaName - ${chapter.number}"

                if (!alreadyDownloaded){
                    val chapterFile = File(mangaFolder, "$chapterName.cbz")
                    try {
                        val pages = proxyService.findPages(manga.alias, chapter.number)
                        val postProcess = pages.postProcess

                        mangaFolder.mkdirs()
                        chapterFile.createNewFile()

                        val out = ZipOutputStream(FileOutputStream(chapterFile))
                        val buffer = out.sink().buffer()
                        println("Download $chapterName")
                        pages.pages.forEachIndexed { index, pageUrl ->
                            val extension = pageUrl.substringAfterLast(".")
                            val pageFormated = "%03d.%s".format((index + 1), extension)
                            val response = proxyService.findPage(pageUrl)

                            val zipEntry = ZipEntry(pageFormated)
                            out.putNextEntry(zipEntry)

                            val source = response.source()
                            buffer.writeAll(source)
                            buffer.flush()
                            print(".")
                        }
                        buffer.close()
                        out.close()
                        println()

                        transaction {
                            Chapters.update({ (Chapters.mangaAlias eq manga.alias) and (Chapters.number eq chapter.number) }){
                                it[downloaded] = true
                            }
                        }
                    } catch (e: Exception) {
                        println("Error when downloading $chapterName")
                        if (chapterFile.exists()) {
                            chapterFile.delete()
                        }
                    }
                } else {
                    println("$chapterName already downloaded")
                }
            }
        }
    }
}

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

data class JapScanManga(val name: String, val alias: String, val thumbnail: String)
data class JapScanChapter(val name: String, val manga: String, val number: String)
data class JapScanPages(val postProcess: String, val pages: List<String>)
data class JapScanDetails(
    val origin: String = "",
    val year: String = "",
    val type: String = "",
    val kind: String = "",
    val author: String = "",
    val summary: String = ""
)


object Mangas : IntIdTable() {
    val alias = varchar("alias", 255)
    val name = varchar("name", 255)
}

object Chapters : IntIdTable() {
    val mangaAlias = varchar("manga_alias", 255) references Mangas.alias
    val number = varchar("number", 255)
    val downloaded = bool("downloaded").default(false)
}