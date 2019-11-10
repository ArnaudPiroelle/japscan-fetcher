package app.japscan.fetcher.db

data class Manga(val name: String, val alias: String)
data class Chapter(val mangaAlias: String, val number: String, val downloaded: Boolean? = null)