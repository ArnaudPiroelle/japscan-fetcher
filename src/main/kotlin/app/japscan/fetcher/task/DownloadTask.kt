package app.japscan.fetcher.task

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.Chapter
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.Manga
import app.japscan.fetcher.db.MangaRepository
import app.japscan.fetcher.notifier.Notifier
import app.japscan.fetcher.processing.MosaicProcessing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.properties.Delegates

class DownloadTask(
    private val notifier: Notifier,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val japScanProxyApiService: JapScanProxyApiService,
    private val outputFolder: File
) {

    private var status: Status by Delegates.observable(Status()) { _, old, new ->
        notifier.notify(new)
    }

    suspend operator fun invoke() {
        println("Get the manga list from database")

        val mangas = mangaRepository.getAll()

        status = status.copy(downloadedManga = 0, totalManga = mangas.size)
        mangas.forEach {
            downloadManga(it)
            status = status.copy(downloadedManga = status.downloadedManga + 1)
        }
    }

    private suspend fun downloadManga(manga: Manga) {
        println("Fetch ${manga.name}")

        val chapters = chapterRepository.getAll(manga.alias)

        status = status.copy(manga = manga.name, downloadedChapter = 0, totalChapter = chapters.size)

        chapters.forEachIndexed { indexChapter, chapter ->
            downloadChapter(manga, indexChapter, chapter)
            status = status.copy(downloadedChapter = status.downloadedChapter + 1)
        }
    }

    private suspend fun downloadChapter(manga: Manga, index: Int, chapter: Chapter) {
        val mangaName = manga.name.replace("/", "-").replace(":", "-")
        val mangaFolder = File(outputFolder, mangaName)
        val alreadyDownloaded = chapterRepository.isDownloaded(manga, chapter)
        val chapterName = "$mangaName - ${chapter.number}".replace(":", "-")
        val chapterFileTmp = File(mangaFolder, "$chapterName.cbz.tmp")
        val chapterFile = File(mangaFolder, "$chapterName.cbz")

        if (chapterFileTmp.exists()) {
            chapterFileTmp.delete()
        }

        if (!alreadyDownloaded) {
            try {
                val pages = japScanProxyApiService.findPages(manga.alias, chapter.number)
                val postProcess = pages.postProcess

                println("postProcess: $postProcess")
                status = status.copy(chapter = chapterName, downloadedPage = 0, totalPage = pages.pages.size)

                mangaFolder.mkdirs()
                chapterFileTmp.createNewFile()

                val out = ZipOutputStream(FileOutputStream(chapterFileTmp))
                val buffer = out.sink().buffer()
                println("Download $chapterName")
                pages.pages.forEachIndexed { index, pageUrl ->
                    downloadPage(postProcess, out, buffer, index, pageUrl)
                    status = status.copy(downloadedPage = status.downloadedPage + 1)
                }
                buffer.close()
                out.close()
                println()

                chapterFileTmp.renameTo(chapterFile)

                chapterRepository.createOrUpdate(chapter.copy(downloaded = true))
            } catch (e: Exception) {
                println("Error when downloading $chapterName")
                e.printStackTrace()
                if (chapterFileTmp.exists()) {
                    chapterFileTmp.delete()
                }

                if (chapterFile.exists()) {
                    chapterFile.delete()
                }
            }
        } else {
            println("$chapterName already downloaded")
        }
    }

    private suspend fun downloadPage(
        postProcess: String,
        zip: ZipOutputStream,
        buffer: BufferedSink,
        index: Int,
        pageUrl: String
    ) = withContext(Dispatchers.IO) {
        val extension = pageUrl.substringAfterLast(".")
        val pageFormated = "%03d.%s".format((index + 1), extension)
        val response = japScanProxyApiService.findPage(pageUrl)

        val zipEntry = ZipEntry(pageFormated)

        zip.putNextEntry(zipEntry)

        val source = response.source()

        when(postProcess){
            "MOSAIC" -> MosaicProcessing.process(source.inputStream(), buffer.outputStream())
            else ->  buffer.writeAll(source)

        }

        buffer.flush()
        source.close()
        print(".")
    }

    data class Status(
        val downloadedManga: Int = 0,
        val totalManga: Int = 0,
        val manga: String = "",
        val chapter: String = "",
        val downloadedChapter: Int = 0,
        val totalChapter: Int = 0,
        val downloadedPage: Int = 0,
        val totalPage: Int = 0
    )
}