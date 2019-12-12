package app.japscan.fetcher

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.MangaRepository
import app.japscan.fetcher.notifier.NotificationManager
import app.japscan.fetcher.notifier.Notifier
import app.japscan.fetcher.notifier.RemoteNotifier
import app.japscan.fetcher.task.DownloadTask
import app.japscan.fetcher.task.FetchTask
import com.google.gson.GsonBuilder
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.exposed.sql.Database
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit


fun main() = runBlocking {
    val downloadPath = "D:\\Downloads\\eBooks\\"
    val db = Database.connect("jdbc:h2:file:$downloadPath/h2", driver = "org.h2.Driver")
    val mangaRepository = MangaRepository(db)
    val chapterRepository = ChapterRepository(db)

    val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    val ebooksFolder = File(downloadPath)

    val existingMangas = mangaRepository.getAll()
    println("Existing mangas in the database: ${existingMangas.map { it.name }}")

    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

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

    val channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
        .usePlaintext()
        .build()

    //val notifierService = NotifierServiceGrpc.newBlockingStub(channel)

    val notifiers = listOf<Notifier>()
    val notificationManager = NotificationManager(notifiers)

    val downloadTask = DownloadTask(notificationManager, mangaRepository, chapterRepository, proxyService, ebooksFolder)
    val fetchTask = FetchTask(mangaRepository, chapterRepository, proxyService, ebooksFolder)
    downloadTask()
}
