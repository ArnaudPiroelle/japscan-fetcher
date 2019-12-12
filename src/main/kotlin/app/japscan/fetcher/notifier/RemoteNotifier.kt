package app.japscan.fetcher.notifier

import app.japscan.fetcher.NotifierServiceGrpc
import app.japscan.fetcher.task.DownloadTask

class RemoteNotifier(private val notifierService: NotifierServiceGrpc.NotifierServiceBlockingStub) :Notifier {
    override fun notify(status: DownloadTask.Status) {
       notifierService.notify(
            app.japscan.fetcher.Notifier.NotifyRequest.newBuilder()
                .setStatus(
                    app.japscan.fetcher.Notifier.Status.newBuilder()
                        .setDownloadedManga(status.downloadedManga)
                        .setTotalManga(status.totalManga)
                        .setManga(status.manga)
                        .setChapter(status.chapter)
                        .setCurrentChapter(status.downloadedChapter)
                        .setTotalChapter(status.totalChapter)
                        .setCurrentPage(status.downloadedPage)
                        .setTotalPage(status.totalPage)
                        .build()
                )
                .build()
        )
    }
}