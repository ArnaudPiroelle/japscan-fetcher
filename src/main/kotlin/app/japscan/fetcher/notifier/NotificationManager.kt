package app.japscan.fetcher.notifier

import com.arnaudpiroelle.notifier.Notifier
import com.arnaudpiroelle.notifier.NotifierServiceGrpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationManager(private val notifierService: NotifierServiceGrpc.NotifierServiceBlockingStub) {
    suspend fun notify(agent: Notifier.Agent): Notifier.NotifyResponse = withContext(Dispatchers.IO) {
        notifierService.notify(
            Notifier.NotifyRequest.newBuilder()
                .setAgent(agent)
                .build()
        )
    }
}