package app.japscan.fetcher.db

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class MangaRepository(private val db: Database) {

    init {
        transaction(db) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Mangas)
        }
    }

    fun getAll(): List<Manga> = transaction(db) {
        Mangas.selectAll().map { Manga(it[Mangas.name], it[Mangas.alias]) }
    }

    fun createOrUpdate(vararg mangas: Manga) = transaction(db) {
        mangas.forEach { manga ->
            val existingManga = Mangas.select { Mangas.alias eq manga.alias }.firstOrNull()
            if (existingManga == null) {
                Mangas.insert {
                    it[alias] = manga.alias
                    it[name] = manga.name
                }
            } else {
                Mangas.update(where = { Mangas.id eq existingManga[Mangas.id] }) {
                    it[alias] = manga.alias
                    it[name] = manga.name
                }
            }
        }
    }
}

object Mangas : IntIdTable() {
    val alias = varchar("alias", 255)
    val name = varchar("name", 255)
}