package com.example.composeup.screenrecord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.composeup.R

class ScreenRecordService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoUri: Uri? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecording()
        }
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_record_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (data != null) {
                    startRecording(resultCode, data)
                }
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (RecordingState.isRecording.value) return

        createNotificationChannel()
        val notification = createNotification("Recording screen...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)

        try {
            setupMediaRecorder()
            setupVirtualDisplay()
            mediaRecorder?.start()
            RecordingState.setRecording(true)
        } catch (e: Exception) {
            Log.e(TAG, "startRecording: ", e)
            e.printStackTrace()
            stopRecording()
        }
    }

    private fun setupMediaRecorder() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // Ensure width and height are even numbers for H.264 encoder
        val width = if (metrics.widthPixels % 2 == 0) metrics.widthPixels else metrics.widthPixels - 1
        val height = if (metrics.heightPixels % 2 == 0) metrics.heightPixels else metrics.heightPixels - 1
        Log.d(TAG, "setupMediaRecorder: size=${width}x${height}, density=${metrics.densityDpi}")

        val fileName = "Recording_${System.currentTimeMillis()}.mp4"
        videoUri = MediaStoreUtils.saveVideoToMediaStore(this, fileName)
        val uri = videoUri ?: throw IllegalStateException("Failed to create MediaStore entry")

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(5 * 1024 * 1024)
            setVideoFrameRate(30)
            setVideoSize(width, height)

            val pfd = contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Failed to open file descriptor for $uri")
            pfd.use { pfd ->
                setOutputFile(pfd.fileDescriptor)
                prepare()
            }
        }
    }

    private fun setupVirtualDisplay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // Use the same even dimensions as MediaRecorder
        val width = if (metrics.widthPixels % 2 == 0) metrics.widthPixels else metrics.widthPixels - 1
        val height = if (metrics.heightPixels % 2 == 0) metrics.heightPixels else metrics.heightPixels - 1

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecordService",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        )
    }

    private fun stopRecording() {
        if (!RecordingState.isRecording.value && mediaRecorder == null) return

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (e: Exception) {
            // Might fail if not started properly
        }

        mediaRecorder?.release()
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        videoUri?.let { MediaStoreUtils.finishRecording(this, it) }
        videoUri = null
        
        RecordingState.setRecording(false)
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
