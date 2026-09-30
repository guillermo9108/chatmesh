package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object VoiceMessageHelper {
    private const val TAG = "VoiceMessageHelper"
    private var activeRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var activePlayer: MediaPlayer? = null
    private var currentPlayingMessageUuid: String? = null
    private var recordingStartTime: Long = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Real audio recording
    fun startRecording(context: Context): Boolean {
        return try {
            stopRecording() // Clean up any lingering recording
            val outputDir = File(context.cacheDir, "audio_messages").apply { mkdirs() }
            val file = File(outputDir, "rec_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(22050)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            activeRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            Log.i(TAG, "Grabación iniciada en: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando grabación de voz", e)
            activeRecorder?.release()
            activeRecorder = null
            currentRecordingFile = null
            false
        }
    }

    fun stopRecording(): Pair<File?, String?> {
        val recorder = activeRecorder ?: return Pair(null, null)
        val file = currentRecordingFile
        activeRecorder = null
        currentRecordingFile = null

        return try {
            val elapsed = System.currentTimeMillis() - recordingStartTime
            if (elapsed < 600) {
                try { Thread.sleep(600 - elapsed) } catch (_: Exception) {}
            }
            recorder.stop()
            recorder.release()
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Log.i(TAG, "Grabación finalizada: ${file.length()} bytes, base64 tamaño: ${base64.length}")
                Pair(file, base64)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizando grabación", e)
            try { recorder.release() } catch (_: Exception) {}
            Pair(null, null)
        }
    }

    fun cancelRecording() {
        try {
            activeRecorder?.stop()
        } catch (_: Exception) {}
        try {
            activeRecorder?.release()
        } catch (_: Exception) {}
        activeRecorder = null
        currentRecordingFile?.delete()
        currentRecordingFile = null
    }

    // Real audio playback
    fun playAudio(
        context: Context,
        messageUuid: String,
        base64Audio: String?,
        localUri: String?,
        onProgress: (Float) -> Unit,
        onCompletion: () -> Unit
    ): Boolean {
        try {
            stopPlayback()

            val fileToPlay = getAudioFile(context, messageUuid, base64Audio, localUri)
            if (fileToPlay == null || !fileToPlay.exists() || fileToPlay.length() == 0L) {
                Log.w(TAG, "No se encontró archivo de audio para reproducir: $messageUuid")
                return false
            }

            val player = MediaPlayer().apply {
                setDataSource(fileToPlay.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    mainHandler.post { onCompletion() }
                }
                start()
            }
            activePlayer = player
            currentPlayingMessageUuid = messageUuid

            // Start progress tracking
            val duration = player.duration.coerceAtLeast(1)
            Thread {
                while (activePlayer == player && player.isPlaying) {
                    val current = player.currentPosition
                    val progress = (current.toFloat() / duration).coerceIn(0f, 1f)
                    mainHandler.post { onProgress(progress) }
                    try { Thread.sleep(100) } catch (_: Exception) {}
                }
            }.start()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error reproduciendo audio", e)
            stopPlayback()
            return false
        }
    }

    fun stopPlayback() {
        try {
            activePlayer?.stop()
        } catch (_: Exception) {}
        try {
            activePlayer?.release()
        } catch (_: Exception) {}
        activePlayer = null
        currentPlayingMessageUuid = null
    }

    fun isPlaying(messageUuid: String): Boolean {
        return activePlayer?.isPlaying == true && currentPlayingMessageUuid == messageUuid
    }

    fun getAudioFile(context: Context, messageUuid: String, base64: String?, localUri: String?): File? {
        // 1. Check if localUri is a valid local file
        if (!localUri.isNullOrBlank()) {
            val local = File(localUri)
            if (local.exists() && local.length() > 0) return local
        }

        // 2. Check if cache file already created for this message UUID
        val cacheDir = File(context.cacheDir, "audio_messages").apply { mkdirs() }
        val target = File(cacheDir, "audio_$messageUuid.m4a")
        if (target.exists() && target.length() > 0) {
            return target
        }

        // 3. Decode base64 to target file
        if (!base64.isNullOrBlank()) {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                FileOutputStream(target).use { it.write(bytes) }
                target
            } catch (e: Exception) {
                Log.e(TAG, "Error decodificando audio base64", e)
                null
            }
        }
        return null
    }
}
