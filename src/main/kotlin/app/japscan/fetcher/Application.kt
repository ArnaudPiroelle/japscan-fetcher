package app.japscan.fetcher

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.MangaRepository
import app.japscan.fetcher.notifier.NotificationManager
import app.japscan.fetcher.notifier.Notifier
import app.japscan.fetcher.task.DownloadTask
import app.japscan.fetcher.task.FetchTask
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.exposed.sql.Database
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

fun main(argv: Array<String>) = runBlocking {

    val fetchCommand = FetchCommand()
    val downloadCommand = DownloadCommand()

    val jc = JCommander.newBuilder()
        .addCommand("fetch", fetchCommand)
        .addCommand("download", downloadCommand)
        .build()

    jc.parse(*argv)

    val command = when (jc.parsedCommand) {
        "fetch" -> fetchCommand
        "download" -> downloadCommand
        else -> null
    }

    if (command == null) {
        jc.usage()
        return@runBlocking
    }

    val db = Database.connect("jdbc:h2:file:${command.output}/mangas", driver = "org.h2.Driver")
    val mangaRepository = MangaRepository(db)
    val chapterRepository = ChapterRepository(db)

    val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    val ebooksFolder = File(command.output)

    val existingMangas = mangaRepository.getAll()
    println("Existing mangas in the database: ${existingMangas.map { it.name }}")

    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(1, 1, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(command.proxy)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val proxyService = retrofit.create(JapScanProxyApiService::class.java)

    /*val channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
        .usePlaintext()
        .build()
*/
    //val notifierService = NotifierServiceGrpc.newBlockingStub(channel)

    val notifiers = listOf<Notifier>()
    val notificationManager = NotificationManager(notifiers)

    val downloadTask = DownloadTask(notificationManager, mangaRepository, chapterRepository, proxyService, ebooksFolder)
    val fetchTask = FetchTask(mangaRepository, chapterRepository, proxyService)

    when (jc.parsedCommand) {
        "fetch" -> fetchTask(command.mangas)
        "download" -> downloadTask(command.mangas)
        else -> jc.usage()
    }

    client.dispatcher.executorService.shutdown();
    client.connectionPool.evictAll();
}

abstract class BaseCommand {
    @Parameter(names = ["-m", "--manga"])
    var mangas = mutableListOf<String>()

    @Parameter(names = ["-p", "--proxy"])
    var proxy = "https://japscan-proxy.herokuapp.com"

    @Parameter(names = ["-o", "--output"])
    var output = "./downloads"
}

@Parameters(commandDescription = "Fetch mangas from japscan website")
class FetchCommand : BaseCommand()

@Parameters(commandDescription = "Download mangas fetched manga")
class DownloadCommand : BaseCommand()