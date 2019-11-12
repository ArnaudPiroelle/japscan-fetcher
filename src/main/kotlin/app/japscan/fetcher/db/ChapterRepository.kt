package app.japscan.fetcher.db

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ChapterRepository(private val db: Database) {

    init {
        transaction(db) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Chapters)
        }
    }

    fun createOrUpdate(vararg chapters: Chapter) = transaction(db) {
        chapters.forEach { chapter ->
            val existingChapter =
                Chapters.select(where = { (Chapters.mangaAlias eq chapter.mangaAlias) and (Chapters.number eq chapter.number) })
                    .firstOrNull()
            if (existingChapter == null) {
                Chapters.insert {
                    it[mangaAlias] = chapter.mangaAlias
                    it[number] = chapter.number
                    if (chapter.downloaded != null) {
                        it[downloaded] = chapter.downloaded
                    }
                }
            } else {
                Chapters.update(where = { Chapters.id eq existingChapter[Chapters.id] }) {
                    it[mangaAlias] = chapter.mangaAlias
                    it[number] = chapter.number
                    if (chapter.downloaded != null) {
                        it[downloaded] = chapter.downloaded
                    }
                }
            }
        }
    }

    fun isDownloaded(manga: Manga, chapter: Chapter) = transaction(db) {
        Chapters.select(where = { (Chapters.mangaAlias eq manga.alias) and (Chapters.number eq chapter.number) }).firstOrNull()?.get(
            Chapters.downloaded
        ) ?: false
    }
}

object Chapters : IntIdTable() {
    val mangaAlias = varchar("manga_alias", 255) references Mangas.alias
    val number = varchar("number", 255)
    val downloaded = bool("downloaded").default(false)
}