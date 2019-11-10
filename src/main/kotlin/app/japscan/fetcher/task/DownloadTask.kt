package app.japscan.fetcher.task

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.Chapter
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.Manga
import app.japscan.fetcher.db.MangaRepository
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadTask(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val japScanProxyApiService: JapScanProxyApiService,
    private val outputFolder: File
) {
    suspend operator fun invoke() {
        println("Get the manga list from japscan")
        val mangas = japScanProxyApiService.findMangas().map { Manga(it.name, it.alias) }

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

            chapters.forEach { chapter ->
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
                        }
                        buffer.close()
                        out.close()
                        println()

                        chapterRepository.createOrUpdate(chapter.copy(downloaded = true))
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