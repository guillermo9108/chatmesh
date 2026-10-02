package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Reproduce el tono de llamada entrante en LOOP y hace vibrar el dispositivo
 * hasta que se llame a stop().
 */
object CallRingtonePlayer {
    private const val TAG = "CallRingtonePlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    @Volatile private var isPlaying = false

    fun start(context: Context) {
        if (isPlaying) return
        isPlaying = true
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "Tono de llamada iniciado en loop")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando tono de llamada", e)
            mediaPlayer = null
        }

        try {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 800, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando vibración", e)
        }
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null
        Log.i(TAG, "Tono de llamada detenido")
    }

    fun isRinging(): Boolean = isPlaying
}
