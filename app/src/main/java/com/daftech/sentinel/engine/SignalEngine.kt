package com.daftech.sentinel.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

// Data 4h BTC/USDT dari Binance Vision (publik, tanpa API key).
// Sumber utama: data-api.binance.vision. Cadangan: api.binance.us.
// App ini PAPER ONLY: tidak ada order, tidak ada API key, tidak ada eksekusi real.

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class SignalResult(
    val lastClose: Double,
    val lastOpenTime: Long,
    val ma20: Double,
    val ma50: Double,
    val donchianHigh: Double,
    val donchianLow: Double,
    val adx: Double,
    val adxRising: Boolean,
    val candleCount: Int,
    val isUptrend: Boolean,
    val isBreakout: Boolean,
    // Keputusan per mode, dihitung dari candle TERAKHIR YANG SUDAH TUTUP.
    // Candle yang masih berjalan TIDAK dipakai sebagai sinyal, untuk hindari repaint.
    val aggressiveAction: String,
    val safeAction: String,
    val aggressiveReason: String,
    val safeReason: String
)

private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private fun fetchFrom(url: String): List<Candle> {
    val req = Request.Builder().url(url)
        .header("User-Agent", "Sentinel/1.0").build()
    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        val body = resp.body?.string() ?: throw Exception("Body kosong")
        val arr = JSONArray(body)
        val out = ArrayList<Candle>()
        for (i in 0 until arr.length()) {
            val k = arr.getJSONArray(i)
            out.add(
                Candle(
                    openTime = k.getLong(0),
                    open = k.getString(1).toDouble(),
                    high = k.getString(2).toDouble(),
                    low = k.getString(3).toDouble(),
                    close = k.getString(4).toDouble(),
                    volume = k.getString(5).toDouble()
                )
            )
        }
        return out
    }
}

fun fetchCandles4h(): List<Candle> {
    val urls = listOf(
        "https://data-api.binance.vision/api/v3/klines?symbol=BTCUSDT&interval=4h&limit=250",
        "https://api.binance.us/api/v3/klines?symbol=BTCUSDT&interval=4h&limit=250"
    )
    var lastError: Exception? = null
    for (url in urls) {
        try {
            val data = fetchFrom(url)
            if (data.size >= 60) return data
        } catch (e: Exception) {
            lastError = e
        }
    }
    throw lastError ?: Exception("Gagal tarik data")
}

private fun sma(values: List<Double>, period: Int, endExclusive: Int): Double {
    var sum = 0.0
    for (i in endExclusive - period until endExclusive) sum += values[i]
    return sum / period
}

// ADX Wilder, disamakan dengan rumus backtest Python Fase 2/3:
// TR, +DM, -DM dihaluskan dengan EMA alpha=1/14 (adjust=False),
// DI+ = 100*plusS/trS, DI- = 100*minusS/trS,
// DX = 100*|DI+ - DI-|/(DI+ + DI-), ADX = EMA DX alpha=1/14.
// Nilai awal = nilai pertama (bukan rata-rata 14 pertama) — sama seperti pandas ewm.
private fun computeAdx(candles: List<Candle>): Pair<Double, Boolean> {
    val n = candles.size
    if (n < 30) return Pair(Double.NaN, false)
    val tr = DoubleArray(n)
    val plusDm = DoubleArray(n)
    val minusDm = DoubleArray(n)
    for (i in 1 until n) {
        val up = candles[i].high - candles[i - 1].high
        val dn = candles[i - 1].low - candles[i].low
        tr[i] = max(
            candles[i].high - candles[i].low,
            max(
                abs(candles[i].high - candles[i - 1].close),
                abs(candles[i].low - candles[i - 1].close)
            )
        )
        plusDm[i] = if (up > dn && up > 0) up else 0.0
        minusDm[i] = if (dn > up && dn > 0) dn else 0.0
    }
    tr[0] = candles[0].high - candles[0].low
    val alpha = 1.0 / 14.0
    fun smooth(values: DoubleArray): DoubleArray {
        val out = DoubleArray(values.size)
        out[0] = values[0]
        for (i in 1 until values.size) out[i] = alpha * values[i] + (1 - alpha) * out[i - 1]
        return out
    }
    val trS = smooth(tr)
    val plusS = smooth(plusDm)
    val minusS = smooth(minusDm)
    val dx = DoubleArray(n)
    for (i in 0 until n) {
        if (trS[i] == 0.0) { dx[i] = 0.0; continue }
        val diP = 100 * plusS[i] / trS[i]
        val diM = 100 * minusS[i] / trS[i]
        val denom = diP + diM
        dx[i] = if (denom == 0.0) 0.0 else 100 * abs(diP - diM) / denom
    }
    val adxArr = smooth(dx)
    val last = adxArr[n - 1]
    val prev = adxArr[n - 2]
    return Pair(last, last > prev)
}

fun computeSignal(all: List<Candle>): SignalResult {
    // Pakai candle terakhir yang sudah tutup: Binance mengembalikan candle
    // berjalan sebagai baris terakhir. Buang baris terakhir itu supaya sinyal
    // tidak berubah-ubah sebelum close.
    val closed = if (all.size >= 2) all.dropLast(1) else all
    require(closed.size >= 60) { "Data kurang dari 60 candle" }
    val closes = closed.map { it.close }
    val n = closed.size
    val ma20 = sma(closes, 20, n)
    val ma50 = sma(closes, 50, n)
    var hh = Double.MIN_VALUE
    var ll = Double.MAX_VALUE
    for (i in n - 21 until n - 1) {
        // 20 candle SEBELUM candle terakhir (prior 20, bukan termasuk terakhir)
        if (closed[i].high > hh) hh = closed[i].high
        if (closed[i].low < ll) ll = closed[i].low
    }
    val last = closed.last()
    val (adx, adxRising) = computeAdx(closed)
    val uptrend = ma20 > ma50
    val breakout = last.close > hh
    val breakdown = last.close < ll

    val aggressiveAction: String
    val aggressiveReason: String
    if (breakout && uptrend) {
        aggressiveAction = "LONG (sinyal)"
        aggressiveReason = "Close di atas Donchian high 20 dan MA20 di atas MA50."
    } else if (breakdown) {
        aggressiveAction = "FLAT / EXIT"
        aggressiveReason = "Close di bawah Donchian low 20: keluar dari posisi long."
    } else if (!uptrend) {
        aggressiveAction = "TUNGGU"
        aggressiveReason = "MA20 belum di atas MA50: tren menengah belum naik."
    } else {
        aggressiveAction = "TUNGGU"
        aggressiveReason = "Belum ada breakout baru di atas Donchian high 20."
    }

    val safeAction: String
    val safeReason: String
    if (aggressiveAction == "LONG (sinyal)" && adx > 25 && adxRising) {
        safeAction = "LONG (sinyal)"
        safeReason = "Sama seperti Agresif, plus ADX di atas 25 dan lagi naik."
    } else if (aggressiveAction == "FLAT / EXIT") {
        safeAction = "FLAT / EXIT"
        safeReason = "Exit tetap berlaku di Mode Aman."
    } else if (aggressiveAction == "LONG (sinyal)") {
        safeAction = "SKIP (tunggu ADX)"
        safeReason = "Breakout ada tapi ADX belum di atas 25 dan naik: kualitas setup rendah."
    } else {
        safeAction = "TUNGGU"
        safeReason = aggressiveReason
    }

    return SignalResult(
        lastClose = last.close,
        lastOpenTime = last.openTime,
        ma20 = ma20,
        ma50 = ma50,
        donchianHigh = hh,
        donchianLow = ll,
        adx = adx,
        adxRising = adxRising,
        candleCount = closed.size,
        isUptrend = uptrend,
        isBreakout = breakout,
        aggressiveAction = aggressiveAction,
        safeAction = safeAction,
        aggressiveReason = aggressiveReason,
        safeReason = safeReason
    )
}
