package app.japscan.fetcher.task

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.Chapter
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.Manga
import app.japscan.fetcher.db.MangaRepository
import java.io.File

class FetchTask(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val japScanProxyApiService: JapScanProxyApiService,
    private val ebooksFolder: File
) {
    suspend operator fun invoke() {
        println("Get the manga list from japscan")

        /*mangaRepository.getAll()
            .forEach { manga ->
                val chapters = chapterRepository.getAll(manga.alias)
                println("${manga.name} : ${chapters.size} chapters")
                chapters.forEach {chapter ->
                    val mangaName = manga.name.replace("/", "-").replace(":", "-")
                    val mangaFolder = File(ebooksFolder, mangaName)
                    val chapterName = "$mangaName - ${chapter.number}".replace(":", "-")
                    val chapterFile = File(mangaFolder, "$chapterName.cbz")

                   chapterRepository.createOrUpdate(chapter.copy(downloaded = chapterFile.exists()))
                }
            }

         */

        japScanProxyApiService.findMangas()
            .map { Manga(it.name, it.alias) }
            .forEach { manga ->
                println("Fetch ${manga.name}")
                val chapters = japScanProxyApiService.findChapters(manga.alias)
                    .map { Chapter(manga.alias, it.number) }

                mangaRepository.createOrUpdate(manga)
                chapterRepository.createOrUpdate(*chapters.toTypedArray())
            }
    }
}