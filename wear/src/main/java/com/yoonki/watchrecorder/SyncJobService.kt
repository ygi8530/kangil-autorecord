package com.yoonki.watchrecorder

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Sends queued recordings to the phone over a Data Layer channel. Runs right after a save, when the
 * phone becomes reachable again (WatchListenerService), and every 15 minutes while the queue is not empty.
 * Items stay queued until the phone acks them, so a dropped connection just means another attempt later.
 */
class SyncJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "sync") {
            runSync(this)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val TAG = "WatchRecorder"
        private val running = AtomicBoolean(false)

        /**
         * One sync at a time: the immediate job, the periodic job and a reconnect event can fire together,
         * which used to send the same file twice. A caller that finds a sync in progress just returns —
         * the running pass reads the queue fresh and covers its items.
         */
        fun runSync(context: Context) {
            if (!running.compareAndSet(false, true)) {
                Log.i(TAG, "sync already running, skipping")
                return
            }
            try {
                syncAll(context)
            } catch (e: Exception) {
                Log.w(TAG, "sync failed: $e")
            } finally {
                running.set(false)
                schedulePeriodicIfNeeded(context)
            }
        }
        private const val JOB_NOW = 1
        private const val JOB_PERIODIC = 2
        private const val RESEND_AFTER_MS = 5 * 60 * 1000L
        private const val TIMEOUT_S = 30L

        /** Run a sync attempt as soon as possible. */
        fun scheduleNow(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_NOW, ComponentName(context, SyncJobService::class.java))
                    .setOverrideDeadline(0)
                    .build()
            )
        }

        private fun schedulePeriodicIfNeeded(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (SyncQueue.all(context).isEmpty()) {
                scheduler.cancel(JOB_PERIODIC)
            } else if (scheduler.getPendingJob(JOB_PERIODIC) == null) {
                scheduler.schedule(
                    JobInfo.Builder(JOB_PERIODIC, ComponentName(context, SyncJobService::class.java))
                        .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                        .setPersisted(true)
                        .build()
                )
            }
        }

        private fun syncAll(context: Context) {
            refreshSettings(context)
            val now = System.currentTimeMillis()
            val due = SyncQueue.all(context).filter { now - it.sentAt > RESEND_AFTER_MS }
            if (due.isEmpty()) return

            val nodes = Tasks.await(
                Wearable.getCapabilityClient(context)
                    .getCapability(SyncProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE),
                TIMEOUT_S, TimeUnit.SECONDS
            ).nodes
            val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (phone == null) {
                Log.i(TAG, "sync: phone not reachable, ${due.size} queued")
                return
            }

            val channels = Wearable.getChannelClient(context)
            for (item in due) {
                val input = try {
                    context.contentResolver.openInputStream(item.uri)
                } catch (e: FileNotFoundException) {
                    null
                }
                if (input == null) {
                    Log.w(TAG, "sync: ${item.name} no longer exists on watch, dropping from queue")
                    SyncQueue.remove(context, item.id)
                    continue
                }
                val channel = Tasks.await(
                    channels.openChannel(phone.id, SyncProtocol.filePath(item.id, item.size, item.name)),
                    TIMEOUT_S, TimeUnit.SECONDS
                )
                try {
                    // Closing the stream half-closes the channel (EOF for the phone); the phone closes the channel
                    // after it has read everything, so we must not close it here or the tail could be cut off.
                    Tasks.await(channels.getOutputStream(channel), TIMEOUT_S, TimeUnit.SECONDS).use { out ->
                        input.use { it.copyTo(out) }
                    }
                    SyncQueue.markSent(context, item.id, System.currentTimeMillis())
                    Log.i(TAG, "sync: sent ${item.name} (${item.size}B) to ${phone.displayName}, waiting for ack")
                } catch (e: Exception) {
                    channels.close(channel)
                    throw e
                }
            }
        }

        /** Pulls the phone's settings data item in case the change event was missed. */
        private fun refreshSettings(context: Context) {
            val items = Tasks.await(Wearable.getDataClient(context).dataItems, TIMEOUT_S, TimeUnit.SECONDS)
            try {
                items.filter { it.uri.path == SyncProtocol.SETTINGS_PATH }.forEach {
                    WatchListenerService.applySettings(context, DataMapItem.fromDataItem(it).dataMap)
                }
            } finally {
                items.release()
            }
        }
    }
}
