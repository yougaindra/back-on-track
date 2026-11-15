package com.yougaindra.backontrack.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.yougaindra.backontrack.model.DailyIntention
import com.yougaindra.backontrack.model.UserSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

object CheckInCuePlayer {

    // 4 warm harmonic intervals (Hz pairs) to prevent acoustic habituation
    private val HARMONIC_PAIRS = listOf(
        Pair(440.0, 659.25), // A4 -> E5 (Perfect 5th)
        Pair(523.25, 659.25), // C5 -> E5 (Major 3rd)
        Pair(392.0, 587.33), // G4 -> D5 (Perfect 5th)
        Pair(440.0, 554.37)  // A4 -> C#5 (Major 3rd)
    )

    private var tts: TextToSpeech? = null

    fun playCheckInCue(context: Context, settings: UserSettings, intention: DailyIntention) {
        val appContext = context.applicationContext

        if (settings.vibrationEnabled) {
            triggerHeartbeatHaptic(appContext)
        }

        if (!settings.soundEnabled && !settings.speakTimeEnabled) {
            return
        }

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = requestAudioDucking(audioManager)

        Thread {
            try {
                if (settings.soundEnabled) {
                    playWarmSynthesizedChime()
                }

                if (settings.speakTimeEnabled) {
                    // 800ms pre-chime buffer so auditory attention opens before speech starts
                    if (settings.soundEnabled) {
                        Thread.sleep(650L)
                    }
                    speakTimeCallout(appContext, settings, intention) {
                        abandonAudioDucking(audioManager, focusRequest)
                    }
                } else {
                    abandonAudioDucking(audioManager, focusRequest)
                }
            } catch (e: Exception) {
                abandonAudioDucking(audioManager, focusRequest)
            }
        }.start()
    }

    /**
     * Distinct tactile signature: 40ms micro-tap -> 150ms pause -> 90ms warm pulse
     */
    private fun triggerHeartbeatHaptic(context: Context) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

            val timings = longArrayOf(0L, 40L, 150L, 90L)
            val amplitudes = intArrayOf(0, 110, 0, 190)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Synthesizes a soft-attack (150ms fade-in), warm-decay two-note chord
     */
    private fun playWarmSynthesizedChime() {
        val sampleRate = 24000
        val durationSec = 1.15
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(totalSamples)

        val (freq1, freq2) = HARMONIC_PAIRS[Random.nextInt(HARMONIC_PAIRS.size)]
        val secondNoteStartSample = (sampleRate * 0.16).toInt()

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate

            // Soft 140ms attack envelope + exponential decay
            val attack1 = (t / 0.14).coerceIn(0.0, 1.0)
            val decay1 = kotlin.math.exp(-3.0 * t)
            val wave1 = sin(2.0 * PI * freq1 * t) * attack1 * decay1

            var wave2 = 0.0
            if (i >= secondNoteStartSample) {
                val t2 = (i - secondNoteStartSample).toDouble() / sampleRate
                val attack2 = (t2 / 0.14).coerceIn(0.0, 1.0)
                val decay2 = kotlin.math.exp(-2.6 * t2)
                wave2 = sin(2.0 * PI * freq2 * t2) * attack2 * decay2
            }

            val mixed = ((wave1 + wave2) * 0.22).coerceIn(-1.0, 1.0)
            pcm[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((durationSec * 1000).toLong())
        track.release()
    }

    private fun speakTimeCallout(
        context: Context,
        settings: UserSettings,
        intention: DailyIntention,
        onComplete: () -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            val timeSpoken = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            val phrase = if (settings.speakIntentionEnabled && intention.primary.isNotBlank()) {
                "$timeSpoken. ${intention.primary}"
            } else {
                timeSpoken
            }

            tts?.shutdown()
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    engine.language = Locale.getDefault()
                    engine.setSpeechRate(0.96f)
                    engine.setPitch(0.98f)

                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(attrs)

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            onComplete()
                        }
                        override fun onError(utteranceId: String?) {
                            onComplete()
                        }
                    })

                    engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "bot_time_callout")
                } else {
                    onComplete()
                }
            }
        }
    }

    private fun requestAudioDucking(audioManager: AudioManager): AudioFocusRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            audioManager.requestAudioFocus(req)
            req
        } else {
            null
        }
    }

    private fun abandonAudioDucking(audioManager: AudioManager, req: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && req != null) {
            audioManager.abandonAudioFocusRequest(req)
        }
    }
}
