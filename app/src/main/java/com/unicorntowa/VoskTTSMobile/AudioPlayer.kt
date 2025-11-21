package com.unicorntowa.VoskTTSMobile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class AudioPlayer(
    private val sampleRate: Int = 22050,
    private val onPlaybackFinished: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }
    private var track: AudioTrack? = null
    private var pcmData: ShortArray? = null
    fun loadPcm(data: ShortArray) {
        release()

        pcmData = data

        val bufferSizeInBytes = data.size * 2 // short = 2 bytes

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val written = audioTrack.write(data, 0, data.size)
        Log.d(TAG, "Loaded $written samples into AudioTrack")

        audioTrack.notificationMarkerPosition = data.size

        audioTrack.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    Log.d(TAG, "Playback finished")
                    onPlaybackFinished?.invoke()
                }

                override fun onPeriodicNotification(track: AudioTrack?) {}
            }
        )

        track = audioTrack
    }
    fun play() {
        try {
            val t = track ?: return

            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                t.stop()
            }

            t.reloadStaticData()
            t.play()

            Log.d(TAG, "Playback started")
        } catch (e: Exception) {
            Log.e(TAG, "play() failed", e)
        }
    }
    fun stop() {
        try {
            val t = track ?: return
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                t.stop()
                Log.d(TAG, "Playback stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "stop() failed", e)
        }
    }
    fun release() {
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }
}
