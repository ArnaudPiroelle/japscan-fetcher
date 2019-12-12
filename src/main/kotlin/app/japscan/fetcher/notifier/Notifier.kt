package app.japscan.fetcher.notifier

import app.japscan.fetcher.task.DownloadTask

interface Notifier {
    fun notify(status: DownloadTask.Status)
}