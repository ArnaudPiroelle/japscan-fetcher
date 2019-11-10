package app.japscan.fetcher.api

data class JapScanManga(val name: String, val alias: String, val thumbnail: String)
data class JapScanChapter(val name: String, val manga: String, val number: String)
data class JapScanPages(val postProcess: String, val pages: List<String>)
data class JapScanDetails(
    val origin: String = "",
    val year: String = "",
    val type: String = "",
    val kind: String = "",
    val author: String = "",
    val summary: String = ""
)