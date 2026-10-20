package com.yoonki.watchrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Microphone foreground service. Records AAC ADTS so a killed process still leaves a playable file,
 * then remuxes it into .m4a when the recording is saved.
 */
class RecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var relativePath: String = DEFAULT_RELATIVE_PATH
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} t=${SystemClock.elapsedRealtime()}")
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_RELATIVE_PATH) ?: DEFAULT_RELATIVE_PATH)
            ACTION_STOP -> stop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(path: String) {
        if (recorder != null) return
        relativePath = path
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            val file = File(filesDir, "rec_${timestamp()}.aac")
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setOutputFile(file)
                prepare()
                start()
            }
            outputFile = file
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WatchRecorder:rec")
                .apply { acquire(MAX_RECORDING_MS) }
            isRunning = true
            vibrate(longArrayOf(0, 80))
            Log.i(TAG, "recording started file=$file")
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            fail("녹음 시작 실패: ${e.javaClass.simpleName}")
        }
    }

    private fun stop() {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "recorder.stop failed", e)
        }
        release()
        if (file != null && file.length() > 0) {
            val uri = publish(file)
            if (uri != null) {
                vibrate(longArrayOf(0, 80, 120, 80))
                Log.i(TAG, "saved uri=$uri path=$relativePath size=${file.length()}")
                file.delete()
            } else {
                fail("저장 실패 (임시 파일 보존됨)")
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish(file: File): android.net.Uri? {
        val m4a = File(cacheDir, file.nameWithoutExtension + ".m4a")
        val output = try {
            remuxToM4a(file, m4a)
            m4a
        } catch (e: Exception) {
            Log.e(TAG, "m4a remux failed, saving raw aac", e)
            m4a.delete()
            file
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Audio.Media.MIME_TYPE, if (output == m4a) "audio/mp4" else "audio/aac")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return try {
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            contentResolver.openOutputStream(uri)!!.use { out -> output.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "publish failed", e)
            null
        } finally {
            m4a.delete()
        }
    }

    /** Copies the AAC frames into an MP4 container without re-encoding. */
    private fun remuxToM4a(src: File, dst: File) {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(dst.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(src.path)
            extractor.selectTrack(0)
            val track = muxer.addTrack(extractor.getTrackFormat(0))
            muxer.start()
            val buffer = ByteBuffer.allocate(64 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
            extractor.release()
        }
    }

    private fun fail(message: String) {
        release()
        vibrate(longArrayOf(0, 600))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        recorder?.release()
        recorder = null
        outputFile = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        isRunning = false
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy recording=${recorder != null}")
        if (recorder != null) stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "녹음", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("녹음 중")
            .setContentText("홈 키 두 번 눌러 종료")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(stopIntent)
            .addAction(Notification.Action.Builder(null, "정지", stopIntent).build())
            .build()
    }

    private fun vibrate(pattern: LongArray) {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        private const val TAG = "WatchRecorder"
        const val ACTION_START = "com.yoonki.watchrecorder.START"
        const val ACTION_STOP = "com.yoonki.watchrecorder.STOP"
        const val EXTRA_RELATIVE_PATH = "relpath"
        const val DEFAULT_RELATIVE_PATH = "Recordings/WatchRecorder/"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RECORDING_MS = 4 * 60 * 60 * 1000L

        @Volatile
        var isRunning = false
            private set
    }
}
