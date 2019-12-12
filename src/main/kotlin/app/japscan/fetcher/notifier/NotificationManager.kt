package app.japscan.fetcher.notifier

import app.japscan.fetcher.task.DownloadTask

class NotificationManager(private val notifiers: List<Notifier>): Notifier {
    override fun notify(status: DownloadTask.Status) {
        notifiers.forEach {
            it.notify(status)
        }
    }
}