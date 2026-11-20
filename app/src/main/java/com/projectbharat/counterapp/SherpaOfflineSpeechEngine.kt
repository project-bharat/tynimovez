package com.projectbharat.counterapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.util.Log
import android.os.Looper
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

class SherpaOfflineSpeechEngine(
    private val modelFiles: OfflineSpeechModelManager.ModelFiles,
    private val english: Boolean,
    private val onTranscript: (String, Int) -> Unit,
    private val onCount: (Int) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onStopped: (String?) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var startupFailure: Throwable? = null

    /**
     * Start is not considered successful until recognizer initialization and microphone
     * acquisition have completed. This prevents the UI from remaining in "Starting"
     * forever when native initialization or AudioRecord setup fails.
     */
    fun start(timeoutMs: Long = 25_000L) {
        check(running.compareAndSet(false, true)) { "Voice engine is already running" }
        startupFailure = null
        val startupSignal = CountDownLatch(1)
        worker = Thread({ captureAndDecode(startupSignal) }, "BilanOfflineVoice").apply {
            isDaemon = true
            start()
        }
        if (!startupSignal.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching { stop() }
            throw IllegalStateException("Voice engine startup timed out. Tap voice count to retry.")
        }
        startupFailure?.let { throw IllegalStateException(it.message ?: "Voice engine failed to start", it) }
        if (!running.get()) throw IllegalStateException("Voice engine stopped during startup. Tap voice count to retry.")
    }

    /**
     * Stop capture and wait until the worker has released AudioRecord and the native
     * recognizer. A timed join can let a language restart open a second microphone
     * while the previous decode thread is still shutting down.
     *
     * Call from an IO/background dispatcher, never the main thread.
     */
    fun stop() {
        running.set(false)
        val activeRecorder = recorder
        try { activeRecorder?.stop() } catch (_: Exception) {}

        val activeWorker = worker
        if (activeWorker != null && activeWorker !== Thread.currentThread()) {
            try {
                activeWorker.join(10_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (activeWorker.isAlive) {
                // Native model initialization/decode can theoretically stall. Never block
                // the Compose effect forever; report the failure and let the UI recover.
                throw IllegalStateException("Voice engine shutdown timed out. Please retry voice count.")
            }
        }

        // The worker's finally block owns release(); only clear references after it exits.
        recorder = null
        worker = null
    }

    private fun captureAndDecode(startupSignal: CountDownLatch) {
        var recognizer: OfflineRecognizer? = null
        var audio: AudioRecord? = null
        var failureMessage: String? = null
        try {
            postStatus(if (english) "Loading offline English model…" else "मराठी ऑफलाइन मॉडेल लोड होत आहे…")
            // Reuse the already-loaded native recognizer on subsequent sessions.
            recognizer = SherpaRecognizerCache.get(modelFiles)

            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "Microphone audio format is not supported" }
            audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SAMPLES * 4)
            )
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            recorder = audio
            try {
                audio.startRecording()
            } catch (error: Exception) {
                throw IllegalStateException("Microphone busy — another app or call is using it", error)
            }
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone unavailable" }
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
            val silenceLimit = (SAMPLE_RATE * 0.65f).toInt()
            val maximumPhrase = SAMPLE_RATE * 6
            val minimumPhrase = SAMPLE_RATE / 4

            while (running.get()) {
                val read = audio.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (read < 0) {
                    if (running.get()) {
                        val reason = when (read) {
                            AudioRecord.ERROR_DEAD_OBJECT -> "Microphone connection was interrupted (dead audio device)"
                            AudioRecord.ERROR_INVALID_OPERATION -> "Microphone stopped unexpectedly"
                            AudioRecord.ERROR_BAD_VALUE -> "Microphone returned an invalid audio buffer"
                            AudioRecord.ERROR -> "Microphone read error"
                            else -> "Microphone read failed ($read)"
                        }
                        throw IllegalStateException(reason)
                    }
                    break
                }
                if (read == 0) {
                    // Avoid a tight CPU spin if the platform temporarily returns an empty frame.
                    Thread.yield()
                    continue
                }
                var energy = 0.0
                val frame = FloatArray(read)
                for (i in 0 until read) {
                    val value = pcm[i] / 32768.0f
                    frame[i] = value
                    energy += value * value
                }
                val rms = sqrt(energy / read).toFloat()
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
                    phraseLength += read
                    frame.forEach { phraseSamples.add(it) }
                } else if (speaking) {
                    silence += read
                    phraseLength += read
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
                    if (phraseLength >= minimumPhrase && running.get()) decodePhrase(recognizer, phraseSamples)
                    phraseSamples.clear()
                    preRoll.clear()
                    speaking = false
                    silence = 0
                    phraseLength = 0
                    if (running.get()) postStatus(if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…")
                }
            }
        } catch (error: Throwable) {
            startupFailure = error
            Log.e(TAG, "Offline voice worker failed", error)
            startupSignal.countDown()
            val detail = error.message.orEmpty()
            failureMessage = when {
                detail.contains("busy", true) || detail.contains("startRecording", true) ->
                    if (english) "Microphone busy — another app or call is using it" else "मायक्रोफोन व्यस्त — दुसरे अॅप किंवा कॉल वापरत आहे"
                detail.contains("Microphone unavailable", true) || detail.contains("audio format", true) ->
                    if (english) "Microphone unavailable" else "मायक्रोफोन उपलब्ध नाही"
                error is SecurityException || detail.contains("permission", true) ->
                    if (english) "Microphone permission required" else "मायक्रोफोन परवानगी आवश्यक"
                else -> "Offline voice error: " + (detail.ifBlank { "could not start" })
            }
        } finally {
            startupSignal.countDown()
            val endedUnexpectedly = running.getAndSet(false)
            try { audio?.stop() } catch (_: Exception) {}
            try { audio?.release() } catch (_: Exception) {}
            recorder = null
            // The process-wide cache owns the recognizer; keep it warm for the next session.
            if (endedUnexpectedly) main.post { onStopped(failureMessage) }
        }
    }

    private fun decodePhrase(recognizer: OfflineRecognizer, samples: List<Float>) {
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples.toFloatArray(), SAMPLE_RATE)
                recognizer.decode(stream)
                val phrase = recognizer.getResult(stream).text.trim()
                if (phrase.isNotBlank()) {
                    val count = extractCount(phrase, english)
                    main.post { if (running.get()) onTranscript(phrase, count) }
                    if (count > 0) main.post { if (running.get()) onCount(count) }
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
        private const val TAG = "BabyVoiceEngine"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 320

        fun extractCount(phrase: String, english: Boolean): Int =
            BabyMovementNumberParser.extract(phrase, english)
    }
}
