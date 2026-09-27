/*
 * Copyright (C) 2026 Meshenger Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package d.d.meshenger.disaster

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import d.d.meshenger.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sqrt

object RubbleAudioProcessor {
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    private val isListening = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var audioThread: Thread? = null

    @Volatile
    var gainMultiplier: Float = 3.0f // Default 3x amplification boost
    var isNoiseSuppressionEnabled: Boolean = true

    interface OnAudioAmplitudeListener {
        fun onAmplitudeChanged(amplitudePercentage: Int, peakDetected: Boolean)
    }

    private var amplitudeListener: OnAudioAmplitudeListener? = null

    fun setOnAudioAmplitudeListener(listener: OnAudioAmplitudeListener?) {
        this.amplitudeListener = listener
    }

    private var audioManager: AudioManager? = null
    @Suppress("DEPRECATION")
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(RubbleAudioProcessor, "Audio focus lost - pausing rubble listening")
                pauseListening()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(RubbleAudioProcessor, "Audio focus gained - resuming rubble listening")
                resumeListening()
            }
        }
    }

    private var isPausedForCall: Boolean = false

    @Synchronized
    fun startListening(context: Context) {
        if (isListening.get()) return

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        requestAudioFocus()

        isListening.set(true)
        isPausedForCall = false

        audioThread = Thread {
            runAudioProcessingLoop()
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stopListening() {
        if (!isListening.get()) return
        isListening.set(false)
        audioThread?.interrupt()
        audioThread = null
        releaseResources()
        abandonAudioFocus()
    }

    @Synchronized
    fun pauseForCall() {
        if (isListening.get()) {
            isPausedForCall = true
            stopListening()
        }
    }

    @Synchronized
    fun resumeAfterCall(context: Context) {
        if (isPausedForCall) {
            isPausedForCall = false
            startListening(context)
        }
    }

    private fun pauseListening() {
        stopListening()
    }

    private fun resumeListening() {
        // Safe resume handling
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        audioManager?.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        audioManager?.abandonAudioFocus(audioFocusChangeListener)
    }

    private fun runAudioProcessingLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufferSize = Math.max(minBufferSize, 2048)
        val audioBuffer = ShortArray(bufferSize)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.d(RubbleAudioProcessor, "AudioRecord initialization failed")
                isListening.set(false)
                return
            }

            // Donanımsal gürültü engelleme (NoiseSuppressor)
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioRecord!!.audioSessionId)
                    noiseSuppressor?.enabled = isNoiseSuppressionEnabled
                } catch (e: Exception) {
                    Log.d(RubbleAudioProcessor, "NoiseSuppressor init error: $e")
                }
            }

            // Donanımsal kazanç kontrolü (AutomaticGainControl)
            if (AutomaticGainControl.isAvailable()) {
                try {
                    automaticGainControl = AutomaticGainControl.create(audioRecord!!.audioSessionId)
                    automaticGainControl?.enabled = true
                } catch (e: Exception) {
                    Log.d(RubbleAudioProcessor, "AGC init error: $e")
                }
            }

            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                audioRecord!!.audioSessionId
            )

            audioRecord?.startRecording()
            audioTrack?.play()

            // Yazılımsal Filtre Değişkenleri (Simple High-Pass Bandpass Filter)
            var prevSample = 0.0f
            val alpha = 0.85f // High pass filter coefficient to cut low rumble

            while (isListening.get() && !Thread.currentThread().isInterrupted) {
                val readCount = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (readCount <= 0) continue

                var sumSquares = 0.0
                for (i in 0 until readCount) {
                    val rawSample = audioBuffer[i].toFloat()

                    // High-pass filter to remove low rumble noise
                    val filteredSample = rawSample - prevSample + alpha * prevSample
                    prevSample = rawSample

                    // Apply Gain Amplification Boost with Soft Clipping
                    var boostedSample = filteredSample * gainMultiplier
                    if (boostedSample > 32767.0f) boostedSample = 32767.0f
                    if (boostedSample < -32768.0f) boostedSample = -32768.0f

                    audioBuffer[i] = boostedSample.toInt().toShort()
                    sumSquares += (boostedSample * boostedSample).toDouble()
                }

                // RMS Calculation for Amplitude & Peak Detection
                val rms = sqrt(sumSquares / readCount)
                val maxRms = 32768.0
                val amplitudePercentage = min(100, ((rms / maxRms) * 350).toInt())
                val peakDetected = amplitudePercentage > 65

                amplitudeListener?.onAmplitudeChanged(amplitudePercentage, peakDetected)

                // Write amplified sound to AudioTrack for real-time listening
                audioTrack?.write(audioBuffer, 0, readCount)
            }
        } catch (e: Exception) {
            Log.d(RubbleAudioProcessor, "Audio processing exception: $e")
        } finally {
            releaseResources()
        }
    }

    private fun releaseResources() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
            automaticGainControl?.release()
            automaticGainControl = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.d(RubbleAudioProcessor, "Resource release error: $e")
        }
    }

    fun isListeningActive(): Boolean = isListening.get()
}
