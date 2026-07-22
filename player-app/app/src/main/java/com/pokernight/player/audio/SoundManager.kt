package com.pokernight.player.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.pokernight.player.audio.PcmSynth.Voice
import com.pokernight.player.audio.PcmSynth.Wave
import kotlin.concurrent.thread

/**
 * 游戏音效管理：1:1 复刻网页版 player.html 的音效引擎与 12 种配方。
 * 全部用 AudioTrack(MODE_STATIC) 现场合成 PCM 播放，无音频素材文件、无第三方库。
 * 开关持久化在独立 prefs（键名 "player_snd"，默认开）——独立文件是为了
 * 不被 AuthManager.logout() 的 prefs.clear() 清掉（网页 localStorage 同样跨登出保留）。
 */
object SoundManager {
    private const val TAG = "SoundManager"
    private const val PREFS_NAME = "sound_prefs"
    private const val KEY_ENABLED = "player_snd"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** 切换并持久化，返回新状态（对应网页 SND.toggle） */
    fun toggle(): Boolean {
        val on = !isEnabled()
        setEnabled(on)
        return on
    }

    /**
     * 播放命名音效；静音时直接返回。
     * 可在任意线程（如 socket 回调线程）调用：合成与播放全部在后台线程，绝不阻塞调用方。
     */
    fun play(name: String) {
        if (!isEnabled()) return
        thread(isDaemon = true, name = "snd-play") {
            var track: AudioTrack? = null
            try {
                val voices = recipe(name) ?: return@thread
                val pcm = PcmSynth.mixToPcm16(voices)
                if (pcm.isEmpty()) return@thread
                val minBuf = AudioTrack.getMinBufferSize(
                    PcmSynth.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(PcmSynth.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, pcm.size))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // 等播放结束后 release（时长 + 少量余量）
                val durMs = pcm.size / 2L * 1000L / PcmSynth.SAMPLE_RATE
                Thread.sleep(durMs + 120)
                track.stop()
            } catch (e: Exception) {
                Log.e(TAG, "play($name) failed", e)
            } finally {
                try {
                    track?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ─── 12 种音效配方，与网页 SND.play 完全一致 ───
    private fun recipe(name: String): List<Voice>? = when (name) {
        "deal" -> listOf(Voice(PcmSynth.slide(900.0, 300.0, 0.12, Wave.TRIANGLE, 0.08)))
        "flip" -> listOf(Voice(PcmSynth.tone(1200.0, 0.07, Wave.SQUARE, 0.05)))
        "chips" -> listOf(
            Voice(PcmSynth.tone(2400.0, 0.05, Wave.SQUARE, 0.05)),
            Voice(PcmSynth.tone(1900.0, 0.06, Wave.SQUARE, 0.05), 0.06),
        )
        "check" -> listOf(Voice(PcmSynth.tone(700.0, 0.06, Wave.SINE, 0.08)))
        "fold" -> listOf(Voice(PcmSynth.slide(500.0, 180.0, 0.18, Wave.SINE, 0.08)))
        "turn" -> listOf(
            Voice(PcmSynth.tone(880.0, 0.12, Wave.SINE, 0.14)),
            Voice(PcmSynth.tone(1320.0, 0.16, Wave.SINE, 0.14), 0.13),
        )
        "reveal" -> listOf(
            Voice(PcmSynth.tone(1046.0, 0.1, Wave.TRIANGLE, 0.1)),
            Voice(PcmSynth.tone(784.0, 0.12, Wave.TRIANGLE, 0.1), 0.1),
        )
        "pot" -> listOf(
            Voice(PcmSynth.tone(1568.0, 0.08, Wave.SQUARE, 0.05)),
            Voice(PcmSynth.tone(2093.0, 0.1, Wave.SQUARE, 0.05), 0.08),
        )
        "win" -> listOf(523.0, 659.0, 784.0, 1046.0).mapIndexed { i, f ->
            Voice(PcmSynth.tone(f, 0.16, Wave.TRIANGLE, 0.12), i * 0.09)
        }
        "elim" -> listOf(400.0, 320.0, 240.0, 160.0).mapIndexed { i, f ->
            Voice(PcmSynth.tone(f, 0.2, Wave.SINE, 0.1), i * 0.11)
        }
        "start" -> listOf(392.0, 523.0, 659.0, 784.0).mapIndexed { i, f ->
            Voice(PcmSynth.tone(f, 0.18, Wave.TRIANGLE, 0.12), i * 0.1)
        }
        "blind" -> listOf(
            Voice(PcmSynth.tone(660.0, 0.09, Wave.SINE, 0.09)),
            Voice(PcmSynth.tone(660.0, 0.09, Wave.SINE, 0.09), 0.14),
        )
        else -> null
    }
}
