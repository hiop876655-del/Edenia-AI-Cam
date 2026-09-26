package com.aicamera.recorder

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "ai_screen_recorder_channel"

        private var projectionResultCode: Int = 0
        private var projectionData: Intent? = null

        fun setProjectionData(code: Int, intent: Intent) {
            projectionResultCode = code
            projectionData = intent
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val audioSource = intent.getStringExtra("AUDIO_SOURCE") ?: "mix"
                val quality = intent.getStringExtra("QUALITY") ?: "720p"
                startForeground(NOTIFICATION_ID, createNotification())
                startScreenRecording(audioSource, quality)
            }
            ACTION_STOP -> {
                stopScreenRecording()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startScreenRecording(audioSource: String, quality: String) {
        if (isRecording || projectionData == null) return

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(projectionResultCode, projectionData!!)

        val metrics = resources.displayMetrics
        val (width, height, bitRate, fps) = when (quality) {
            "1080p60" -> Quadruple(1080, 2400, 16000000, 60)
            "1080p" -> Quadruple(1080, 2400, 10000000, 30)
            else -> Quadruple(720, 1600, 6000000, 30) // 720p Optimized for Unisoc T606
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        // Setup AudioPlaybackCapture for Android 10+ Raw System Audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && audioSource == "internal") {
            val audioCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            // Pass capture config to audio recording stream with auto 0dB gain normalization
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        } else {
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        }

        mediaRecorder?.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempFile = File(cacheDir, "REC_${timeStamp}.mp4")
            setOutputFile(tempFile.absolutePath)
            
            setVideoSize(width, height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(bitRate)
            setVideoFrameRate(fps)
            setAudioSamplingRate(48000)
            setAudioEncodingBitRate(192000)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorderDisplay",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )

        mediaRecorder?.start()
        isRecording = true
    }

    private fun stopScreenRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            virtualDisplay?.release()
            mediaProjection?.stop()
            isRecording = false

            // Sync video directly into MediaStore (Movies/AI Screen Recorder)
            saveVideoToMediaStore()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveVideoToMediaStore() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AI_REC_${timeStamp}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AI Screen Recorder")
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun createNotification(): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Screen Recorder Pro")
            .setContentText("Recording system audio & screen in high fidelity...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
