package app.japscan.fetcher.task

import app.japscan.fetcher.api.JapScanProxyApiService
import app.japscan.fetcher.db.Chapter
import app.japscan.fetcher.db.ChapterRepository
import app.japscan.fetcher.db.Manga
import app.japscan.fetcher.db.MangaRepository

class FetchTask(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val japScanProxyApiService: JapScanProxyApiService
) {
    suspend operator fun invoke(mangas: List<String>) {
        println("Get the manga list from japscan")

        japScanProxyApiService.findMangas()
            .filter { mangas.isEmpty() || it.alias in mangas }
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