package com.aicamera.recorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL_RECORDER = "com.aicamera.recorder/screen"
    private val CHANNEL_MEDIASTORE = "com.aicamera.recorder/mediastore"
    private val CHANNEL_OVERLAY = "com.aicamera.recorder/overlay"
    
    private val REQUEST_MEDIA_PROJECTION = 1001
    private var pendingResult: MethodChannel.Result? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // MethodChannel for Screen Recording
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_RECORDER).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestScreenCapturePermission" -> {
                    pendingResult = result
                    val intent = mediaProjectionManager?.createScreenCaptureIntent()
                    startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
                }
                "startRecording" -> {
                    val audioSource = call.argument<String>("audioSource") ?: "mix"
                    val quality = call.argument<String>("quality") ?: "720p"
                    val resultCode = call.argument<Int>("resultCode") ?: Activity.RESULT_OK
                    
                    val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                        action = ScreenRecordService.ACTION_START
                        putExtra("AUDIO_SOURCE", audioSource)
                        putExtra("QUALITY", quality)
                        putExtra("RESULT_CODE", resultCode)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    result.success(true)
                }
                "stopRecording" -> {
                    val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                        action = ScreenRecordService.ACTION_STOP
                    }
                    startService(serviceIntent)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }

        // Overlay Permission for Floating Camera Bubble
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_OVERLAY).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkOverlayPermission" -> {
                    val hasPerm = Settings.canDrawOverlays(this)
                    result.success(hasPerm)
                }
                "requestOverlayPermission" -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                ScreenRecordService.setProjectionData(resultCode, data)
                pendingResult?.success(mapOf("granted" to true, "resultCode" to resultCode))
            } else {
                pendingResult?.success(mapOf("granted" to false))
            }
            pendingResult = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
