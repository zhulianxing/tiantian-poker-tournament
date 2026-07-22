package com.pokernight.player.audio

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * PCM 合成核心（纯 Kotlin、无 Android 依赖，可单独在 JVM 上验证）。
 * 语义 1:1 复刻网页版 player.html 的 Web Audio 音效引擎：
 * - tone：恒频音，指数包络（12ms 内升到 vol，dur 末指数降到 0.0001）
 * - slide：频率 f1→f2 指数滑动，指数包络（15ms 上升）
 * 输出 16bit mono PCM，采样率 44100。
 */
object PcmSynth {
    const val SAMPLE_RATE = 44100

    enum class Wave { SINE, SQUARE, TRIANGLE }

    /** 一个发音体：采样数据 + 起始延迟（秒），多个 Voice 按 delay 叠加混音 */
    class Voice(val samples: FloatArray, val delaySec: Double = 0.0)

    /** 与 Web Audio exponentialRampToValueAtTime 一致：v0→v1 按指数插值 */
    private fun expRamp(v0: Double, v1: Double, pos: Double): Double =
        v0 * (v1 / v0).pow(pos)

    /** 包络：t<attack 时 0.0001→vol 指数上升，随后 vol→0.0001 指数衰减至 dur 末 */
    private fun envelope(t: Double, dur: Double, vol: Double, attack: Double): Double {
        val floor = 0.0001
        return if (t < attack) {
            expRamp(floor, vol, t / attack)
        } else {
            expRamp(vol, floor, ((t - attack) / (dur - attack)).coerceAtMost(1.0))
        }
    }

    private fun waveValue(wave: Wave, phase: Double): Double = when (wave) {
        Wave.SINE -> sin(phase)
        Wave.SQUARE -> sign(sin(phase))
        Wave.TRIANGLE -> 2.0 / PI * asin(sin(phase))
    }

    /** 恒频音，对应网页 SND.tone(freq, dur, type, vol)（attack 12ms） */
    fun tone(freqHz: Double, durSec: Double, wave: Wave, vol: Double): FloatArray {
        val n = (durSec * SAMPLE_RATE).roundToInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            out[i] = (waveValue(wave, 2.0 * PI * freqHz * t) * envelope(t, durSec, vol, 0.012)).toFloat()
        }
        return out
    }

    /** 滑音，对应网页 SND.slide(f1, f2, dur, type, vol)：频率指数滑动（attack 15ms） */
    fun slide(f1: Double, f2: Double, durSec: Double, wave: Wave, vol: Double): FloatArray {
        val n = (durSec * SAMPLE_RATE).roundToInt()
        val out = FloatArray(n)
        val k = ln(f2 / f1) / durSec
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            // 相位 = 2π·∫f(t)dt，f(t)=f1·e^(kt) → 已振动周数 cycles = f1·(e^(kt)-1)/k
            val cycles = f1 * (exp(k * t) - 1.0) / k
            out[i] = (waveValue(wave, 2.0 * PI * cycles) * envelope(t, durSec, vol, 0.015)).toFloat()
        }
        return out
    }

    /** 多路 Voice 按 delay 叠加混音 → 16bit 小端 PCM；越界硬截幅（与 Web Audio 行为一致） */
    fun mixToPcm16(voices: List<Voice>): ByteArray {
        var total = 0
        for (v in voices) {
            val end = (v.delaySec * SAMPLE_RATE).roundToInt() + v.samples.size
            if (end > total) total = end
        }
        val mix = FloatArray(total)
        for (v in voices) {
            val off = (v.delaySec * SAMPLE_RATE).roundToInt()
            for (i in v.samples.indices) mix[off + i] += v.samples[i]
        }
        val pcm = ByteArray(total * 2)
        for (i in 0 until total) {
            val s = (mix[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
            pcm[i * 2] = (s and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return pcm
    }
}
