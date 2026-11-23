package com.projectbharat.counterapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Local microphone capture with silence-based utterance segmentation and offline decoding. */
/** Keeps the native recognizer warm between sessions; only one language model stays resident. */
private object SherpaRecognizerCache {
    private var cachedKey: String? = null
    private var cached: OfflineRecognizer? = null

    @Synchronized
    fun get(files: OfflineSpeechModelManager.ModelFiles): OfflineRecognizer {
        val key = files.model.absolutePath + "|" + files.tokens.absolutePath
        if (cachedKey == key && cached != null) return cached!!
        cached?.release()
        cached = null
        cachedKey = null
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = files.model.absolutePath),
                tokens = files.tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "nemo"
            )
        )
        return OfflineRecognizer(config = config).also {
            cached = it
            cachedKey = key
        }
    }
}

internal class MicrophoneUnavailable(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal class SherpaOfflineSpeechEngine(
    context: Context,
    private val modelFiles: OfflineSpeechModelManager.ModelFiles,
    private val english: Boolean,
    private val previewRequested: () -> Boolean,
    private val onTranscript: (String, Int) -> Unit,
    private val onCount: (Int) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onStopped: (Throwable) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    @Volatile private var worker: Thread? = null
    @Volatile private var startupFailure: Throwable? = null

    /**
     * Start is not considered successful until recognizer initialization and microphone
     * acquisition have completed. This prevents the UI from remaining in "Starting"
     * forever when native initialization or AudioRecord setup fails.
     */
    fun start(timeoutMs: Long = 25_000L) {
        check(worker == null) { "Voice engine has already been started" }
        if (!captureOwned.compareAndSet(false, true)) {
            throw MicrophoneUnavailable("Previous voice worker is still shutting down")
        }
        val startupSignal = CountDownLatch(1)
        try {
            running.set(true)
            val captureThread = Thread({ captureAndDecode(startupSignal) }, "BilanOfflineVoice")
            captureThread.isDaemon = true
            worker = captureThread
            captureThread.start()
        } catch (error: Throwable) {
            running.set(false)
            captureOwned.set(false)
            throw error
        }
        if (!startupSignal.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw MicrophoneUnavailable("Voice engine startup timed out")
        }
        startupFailure?.let { throw it }
    }

    // Only the worker touches AudioRecord and the native cache. A timeout leaves
    // ownership with that worker until its cleanup actually finishes.
    fun stop() {
        running.set(false)
        val activeWorker = worker ?: return
        activeWorker.join(3_000L)
        if (activeWorker.isAlive) Log.w(TAG, "Voice worker is still shutting down")
    }

    private fun checkMicrophoneAccess(audio: AudioRecord? = null) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission required")
        }
        when (audioManager?.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION ->
                throw MicrophoneUnavailable("Microphone busy during a call")
        }
        if (Build.VERSION.SDK_INT >= 29 && audio?.activeRecordingConfiguration?.isClientSilenced == true) {
            throw MicrophoneUnavailable("Android silenced microphone capture")
        }
    }

    private fun captureAndDecode(startupSignal: CountDownLatch) {
        val previews = java.util.concurrent.Executors.newSingleThreadExecutor()
        var preview: java.util.concurrent.Future<*>? = null
        var audio: AudioRecord? = null
        var failure: Throwable? = null
        try {
            checkMicrophoneAccess()
            postStatus(if (english) "Preparing voice counting…" else "आवाज मोजणी तयार होत आहे…")
            // Reuse the already-loaded native recognizer on subsequent sessions.
            val recognizer = SherpaRecognizerCache.get(modelFiles)

            if (!running.get()) return
            checkMicrophoneAccess()
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "Microphone audio format is not supported" }
            audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SAMPLES * 4)
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                throw MicrophoneUnavailable("Microphone unavailable")
            }
            if (!running.get()) return
            try {
                audio.startRecording()
            } catch (error: IllegalStateException) {
                throw MicrophoneUnavailable("Microphone unavailable", error)
            }
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicrophoneUnavailable("Microphone unavailable")
            }
            startupSignal.countDown()
            postStatus(if (english) "Listening offline — say a number from one to ten" else "ऑफलाइन ऐकत आहे — एक ते दहा म्हणा")

            val pcm = ShortArray(FRAME_SAMPLES)
            val phraseSamples = ArrayList<Float>(SAMPLE_RATE * 5)
            // Keep a short rolling buffer so the first syllable is not clipped by the voice gate.
            val preRoll = java.util.ArrayDeque<FloatArray>()
            val preRollFrames = 10 // 200 ms at 16 kHz / 20 ms per frame
            var noiseFloor = 0.002f
            var speaking = false
            var silence = 0
            var phraseLength = 0
            var nextPreview = SAMPLE_RATE
            val silenceLimit = (SAMPLE_RATE * 0.65f).toInt()
            val maximumPhrase = SAMPLE_RATE * 6
            val minimumPhrase = SAMPLE_RATE / 4

            var bufferedSamples = 0
            var nextAccessCheck = 0L
            while (running.get()) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextAccessCheck) {
                    checkMicrophoneAccess(audio)
                    nextAccessCheck = now + 500
                }
                val read = audio.read(pcm, bufferedSamples, pcm.size - bufferedSamples, AudioRecord.READ_NON_BLOCKING)
                if (read < 0) {
                    if (running.get()) {
                        val reason = when (read) {
                            AudioRecord.ERROR_DEAD_OBJECT -> "Microphone connection was interrupted (dead audio device)"
                            AudioRecord.ERROR_INVALID_OPERATION -> "Microphone stopped unexpectedly"
                            AudioRecord.ERROR_BAD_VALUE -> "Microphone returned an invalid audio buffer"
                            AudioRecord.ERROR -> "Microphone read error"
                            else -> "Microphone read failed ($read)"
                        }
                        if (read == AudioRecord.ERROR_BAD_VALUE) throw IllegalStateException(reason)
                        throw MicrophoneUnavailable(reason)
                    }
                    break
                }
                if (read == 0) {
                    Thread.sleep(10)
                    continue
                }
                bufferedSamples += read
                if (bufferedSamples < pcm.size) continue
                bufferedSamples = 0
                var energy = 0.0
                val frame = FloatArray(pcm.size)
                for (i in pcm.indices) {
                    val value = pcm[i] / 32768.0f
                    frame[i] = value
                    energy += value * value
                }
                val rms = sqrt(energy / pcm.size).toFloat()
                // Track quiet-room noise slowly and clamp the gate to avoid suppressing soft speech.
                val voiceThreshold = (noiseFloor * 2.8f).coerceIn(0.0045f, 0.018f)
                val hasVoice = rms >= voiceThreshold

                if (hasVoice) {
                    if (!speaking) {
                        speaking = true
                        while (preRoll.isNotEmpty()) {
                            val earlierFrame = preRoll.removeFirst()
                            earlierFrame.forEach { phraseSamples.add(it) }
                            phraseLength += earlierFrame.size
                        }
                    }
                    silence = 0
                    phraseLength += pcm.size
                    frame.forEach { phraseSamples.add(it) }
                } else if (speaking) {
                    silence += pcm.size
                    phraseLength += pcm.size
                    frame.forEach { phraseSamples.add(it) }
                } else {
                    noiseFloor = (noiseFloor * 0.95f + rms * 0.05f).coerceIn(0.0005f, 0.012f)
                    preRoll.addLast(frame)
                    while (preRoll.size > preRollFrames) preRoll.removeFirst()
                }

                if (speaking && (silence >= silenceLimit || phraseLength >= maximumPhrase)) {
                    // Phrase buffering is strictly bounded (6 seconds). Log memory pressure
                    // at segment boundaries rather than allocating diagnostics every audio frame.
                    val runtime = Runtime.getRuntime()
                    val usedBytes = runtime.totalMemory() - runtime.freeMemory()
                    val maxBytes = runtime.maxMemory()
                    if (maxBytes > 0L && usedBytes > maxBytes * 0.85) {
                        Log.w(TAG, "High heap use at phrase boundary: ${usedBytes / (1024 * 1024)}MB / ${maxBytes / (1024 * 1024)}MB")
                    }
                    preview?.get()
                    if (phraseLength >= minimumPhrase && running.get()) decodePhrase(recognizer, phraseSamples.toFloatArray(), final = true)
                    nextPreview = SAMPLE_RATE
                    phraseSamples.clear()
                    preRoll.clear()
                    speaking = false
                    silence = 0
                    phraseLength = 0
                    if (running.get()) postStatus(if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…")
                } else if (speaking && hasVoice && phraseLength >= nextPreview && previewRequested() &&
                    preview?.isDone != false) {
                    val samples = phraseSamples.toFloatArray()
                    nextPreview = phraseLength + SAMPLE_RATE
                    preview = previews.submit { decodePhrase(recognizer, samples, final = false) }
                }
            }
        } catch (error: Throwable) {
            failure = if (error is LinkageError) IllegalStateException("Native voice engine unavailable", error) else error
            startupFailure = failure
            Log.e(TAG, "Offline voice worker failed", error)
        } finally {
            val endedUnexpectedly = running.getAndSet(false)
            // Unblock start even if Android hangs while releasing a failed recorder.
            startupSignal.countDown()
            if (endedUnexpectedly && failure != null) {
                val error = failure
                main.post { onStopped(error) }
            }
            try { audio?.stop() } catch (_: Exception) {}
            try {
                audio?.release()
                previews.shutdown()
                // Keep ownership while a native preview is still using the cached model.
                preview?.get()
                captureOwned.set(false)
            } catch (error: Exception) {
                Log.e(TAG, "Could not release microphone; retaining capture ownership", error)
            }
        }
    }

    private fun decodePhrase(recognizer: OfflineRecognizer, samples: FloatArray, final: Boolean) {
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                recognizer.decode(stream)
                val phrase = recognizer.getResult(stream).text.trim()
                if (phrase.isNotBlank()) {
                    val count = extractCount(phrase, english)
                    main.post { if (running.get()) onTranscript(phrase, count) }
                    if (final && count > 0) main.post { if (running.get()) onCount(count) }
                }
            } finally {
                stream.release()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Phrase decode failed; continuing capture", error)
            postStatus(if (english) "Could not decode that phrase. Keep speaking clearly." else "हा शब्दसमूह ओळखता आला नाही. स्पष्ट बोला.")
        }
    }

    private fun postStatus(value: String) {
        main.post { if (running.get()) onStatus(value) }
    }

    companion object {
        private val captureOwned = AtomicBoolean(false)
        private const val TAG = "BabyVoiceEngine"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 320

        fun extractCount(phrase: String, english: Boolean): Int =
            BabyMovementNumberParser.extract(phrase, english)
    }
}
