package app.japscan.fetcher.task

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.Chapter
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.Manga
import app.japscan.fetcher.db.MangaRepository
import app.japscan.fetcher.notifier.NotificationManager
import com.arnaudpiroelle.notifier.Notifier
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadTask(
    private val notificationManager: NotificationManager,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val japScanProxyApiService: JapScanProxyApiService,
    private val outputFolder: File
) {
    suspend operator fun invoke() {
        println("Get the manga list from japscan")
        val mangas = japScanProxyApiService.findMangas().map { Manga(it.name, it.alias) }

        val agentBuilder = Notifier.Agent.newBuilder()
            .setName(InetAddress.getLocalHost().hostName)
            .setTotalManga(mangas.size)
            .setDownloadedManga(0)

        mangas.forEach { manga ->
            println("Fetch ${manga.name}")
            val mangaName = manga.name.replace("/", "-")
            val mangaFolder = File(outputFolder, mangaName)

            val chapters = japScanProxyApiService.findChapters(manga.alias).map {
                Chapter(
                    manga.alias,
                    it.number
                )
            }
            mangaRepository.createOrUpdate(manga)
            chapterRepository.createOrUpdate(*chapters.toTypedArray())

            val taskBuilder = Notifier.Task.newBuilder()
                .setName(Thread.currentThread().name)
                .setTotalChapter(chapters.size)

            chapters.forEachIndexed { indexChapter, chapter ->
                val alreadyDownloaded = chapterRepository.isDownloaded(manga, chapter)
                val chapterName = "$mangaName - ${chapter.number}"

                if (!alreadyDownloaded) {
                    val chapterFile = File(mangaFolder, "$chapterName.cbz")
                    try {
                        val pages = japScanProxyApiService.findPages(manga.alias, chapter.number)
                        val postProcess = pages.postProcess

                        mangaFolder.mkdirs()
                        chapterFile.createNewFile()

                        val out = ZipOutputStream(FileOutputStream(chapterFile))
                        val buffer = out.sink().buffer()
                        println("Download $chapterName")
                        pages.pages.forEachIndexed { index, pageUrl ->
                            val extension = pageUrl.substringAfterLast(".")
                            val pageFormated = "%03d.%s".format((index + 1), extension)
                            val response = japScanProxyApiService.findPage(pageUrl)

                            val zipEntry = ZipEntry(pageFormated)
                            out.putNextEntry(zipEntry)

                            val source = response.source()
                            buffer.writeAll(source)
                            buffer.flush()
                            print(".")

                            notificationManager.notify(
                                agentBuilder.clearTasks()
                                    .addAllTasks(
                                        listOf(
                                            taskBuilder.setTotalPage(pages.pages.size)
                                                .setCurrentChapter(indexChapter)
                                                .setCurrentPage(index)
                                                .setManga(manga.name)
                                                .setChapter(chapter.number)
                                                .build()
                                        )
                                    )
                                    .build()
                            )
                        }
                        buffer.close()
                        out.close()
                        println()

                        chapterRepository.createOrUpdate(chapter.copy(downloaded = true))
                    } catch (e: Exception) {
                        println("Error when downloading $chapterName")
                        e.printStackTrace()
                        if (chapterFile.exists()) {
                            chapterFile.delete()
                        }
                    }
                } else {
                    println("$chapterName already downloaded")
                }
            }

            agentBuilder.downloadedManga = agentBuilder.downloadedManga++
        }
    }
}