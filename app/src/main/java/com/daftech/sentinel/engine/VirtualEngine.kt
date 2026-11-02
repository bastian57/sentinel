package com.daftech.sentinel.engine

import kotlin.math.abs
import kotlin.math.max

// SENTINEL v2 — Simulator Disiplin, VIRTUAL ONLY.
// Pure Kotlin, tanpa dependensi Android, supaya bisa di-unit-test.
// Aturan replay DISAMAKAN dengan backtest Python Fase 2/3 + spec Stage-1 v2:
//  - Sinyal dari candle TUTUP, eksekusi virtual di open candle berikutnya.
//  - LONG hanya: breakout Donchian high prior-20 DAN MA20 > MA50.
//    Mode Aman tambah ADX14 > 25 dan naik.
//  - EXIT: close di bawah Donchian low prior-20.
//  - Fee 0,1% per sisi, 1 posisi, modal virtual $10.000 per mode.
//  - Warmup: tidak ada sinyal sebelum candle ke-50 (MA50).
//  - Cooldown: setelah trade LOSS ditutup, entry signal 3 candle berikutnya
//    di-skip (signal index < exitSignalIndex + 4 tidak boleh entry).
//  - Circuit breaker: DD dari puncak (termasuk posisi terbuka, dinilai
//    close terakhir) <= -30% -> pause, tidak ada entry baru. Posisi terbuka
//    tetap di-exit oleh Donchian low. Warning di -25%.
//  - V3 dan Kronos TIDAK dipakai di sini. Hanya G dan Aman.

const val VIRTUAL_CAPITAL = 10000.0
const val VIRTUAL_FEE = 0.001
const val VIRTUAL_CANDLE_MS = 4L * 60 * 60 * 1000

data class VirtualTrade(
    val entrySignalTime: Long,
    val entryPrice: Double,
    val exitSignalTime: Long?,
    val exitPrice: Double?,
    // Return sudah termasuk fee 2 sisi, null kalau posisi masih terbuka.
    val returnFraction: Double?,
    val isOpen: Boolean,
    val isPanic: Boolean = false,
    val panicPrice: Double? = null
) {
    val isWin: Boolean get() = (returnFraction ?: 0.0) > 0.0
}

data class RobotReplay(
    val finalEquity: Double,
    val maxDrawdownPct: Double,
    val trades: List<VirtualTrade>,
    val openTrade: VirtualTrade?,
    val breakerPaused: Boolean,
    val breakerWarning: Boolean
)

data class UserReplay(
    val finalEquity: Double,
    val followedTrades: List<VirtualTrade>,
    val followedCount: Int,
    val skippedCount: Int,
    val panicCount: Int,
    val missedWinnerCount: Int,
    val missedWinnerDollars: Double,
    val avoidedLoserCount: Int,
    val avoidedLoserDollars: Double,
    val followRatePct: Double,
    val openTrade: VirtualTrade?
)

// ADX Wilder, sama persis dengan SignalEngine.computeAdx, tapi mengembalikan
// seluruh array supaya replay bisa memakai ADX per candle.
fun computeAdxArray(candles: List<Candle>): DoubleArray {
    val n = candles.size
    val out = DoubleArray(n) { Double.NaN }
    if (n < 30) return out
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
        val result = DoubleArray(values.size)
        result[0] = values[0]
        for (i in 1 until values.size) result[i] = alpha * values[i] + (1 - alpha) * result[i - 1]
        return result
    }
    val trS = smooth(tr)
    val plusS = smooth(plusDm)
    val minusS = smooth(minusDm)
    val dx = DoubleArray(n)
    for (i in 0 until n) {
        if (trS[i] == 0.0) {
            dx[i] = 0.0
            continue
        }
        val diP = 100 * plusS[i] / trS[i]
        val diM = 100 * minusS[i] / trS[i]
        val denom = diP + diM
        dx[i] = if (denom == 0.0) 0.0 else 100 * abs(diP - diM) / denom
    }
    return smooth(dx)
}

private fun sma(closes: List<Double>, period: Int, endInclusive: Int): Double {
    var sum = 0.0
    for (i in endInclusive - period + 1..endInclusive) sum += closes[i]
    return sum / period
}

// Cek HALT: return null kalau data OK, atau alasan HALT kalau tidak.
fun checkHalt(closed: List<Candle>): String? {
    if (closed.size < 50) return "Data kurang dari 50 candle (warmup belum cukup)."
    val seen = HashSet<Long>()
    for (c in closed) {
        if (!seen.add(c.openTime)) return "Timestamp duplikat di data."
    }
    for (i in 1 until closed.size) {
        if (closed[i].openTime - closed[i - 1].openTime != VIRTUAL_CANDLE_MS) {
            return "Gap data: ada candle hilang di antara data yang ditarik."
        }
    }
    return null
}

fun replayRobot(closed: List<Candle>, runningOpen: Double?, safeMode: Boolean): RobotReplay {
    val n = closed.size
    if (n < 50) {
        return RobotReplay(VIRTUAL_CAPITAL, 0.0, emptyList(), null, false, false)
    }
    val closes = closed.map { it.close }
    val adx = computeAdxArray(closed)
    // Sinyal per candle tutup.
    val entrySignal = BooleanArray(n)
    val exitSignal = BooleanArray(n)
    for (i in 50 until n) {
        var hh = Double.MIN_VALUE
        var ll = Double.MAX_VALUE
        for (j in i - 20 until i) {
            if (closed[j].high > hh) hh = closed[j].high
            if (closed[j].low < ll) ll = closed[j].low
        }
        val ma20 = sma(closes, 20, i)
        val ma50 = sma(closes, 50, i)
        val uptrend = ma20 > ma50
        val breakout = closes[i] > hh
        val breakdown = closes[i] < ll
        val safeOk = !safeMode || (adx[i].isFinite() && adx[i - 1].isFinite() && adx[i] > 25 && adx[i] > adx[i - 1])
        entrySignal[i] = breakout && uptrend && safeOk
        exitSignal[i] = breakdown
    }
    var cash = VIRTUAL_CAPITAL
    var btc = 0.0
    var inPos = false
    var entryPrice = 0.0
    var entrySignalTime = 0L
    var cooldownUntil = -1
    var paused = false
    var peak = VIRTUAL_CAPITAL
    var maxDd = 0.0
    val trades = ArrayList<VirtualTrade>()
    var openTrade: VirtualTrade? = null
    // execIndex = candle yang open-nya dipakai eksekusi. execIndex == n berarti
    // candle berjalan (running), open-nya diketahui, close-nya belum.
    for (execIndex in 0..n) {
        val priceOpen: Double? = if (execIndex < n) closed[execIndex].open else runningOpen
        if (priceOpen == null) break
        val sigIndex = execIndex - 1
        if (sigIndex >= 0 && inPos && exitSignal[sigIndex]) {
            val ret = (priceOpen / entryPrice) * (1 - VIRTUAL_FEE) * (1 - VIRTUAL_FEE) - 1.0
            cash += btc * priceOpen * (1 - VIRTUAL_FEE)
            btc = 0.0
            inPos = false
            trades.add(
                VirtualTrade(
                    entrySignalTime = entrySignalTime,
                    entryPrice = entryPrice,
                    exitSignalTime = closed[sigIndex].openTime,
                    exitPrice = priceOpen,
                    returnFraction = ret,
                    isOpen = false
                )
            )
            if (ret < 0) cooldownUntil = sigIndex + 4
            openTrade = null
        } else if (sigIndex >= 0 && !inPos && entrySignal[sigIndex] && !paused && sigIndex >= cooldownUntil) {
            val invest = cash
            btc = (invest * (1 - VIRTUAL_FEE)) / priceOpen
            cash = 0.0
            entryPrice = priceOpen
            entrySignalTime = closed[sigIndex].openTime
            inPos = true
            openTrade = VirtualTrade(
                entrySignalTime = entrySignalTime,
                entryPrice = entryPrice,
                exitSignalTime = null,
                exitPrice = null,
                returnFraction = null,
                isOpen = true
            )
        }
        // Valuasi untuk DD: close candle execIndex, atau close terakhir kalau running.
        val valuationClose = if (execIndex < n) closed[execIndex].close else closed[n - 1].close
        val equity = cash + if (inPos) btc * valuationClose else 0.0
        if (equity > peak) peak = equity
        val dd = (equity - peak) / peak * 100.0
        if (dd < maxDd) maxDd = dd
        if (dd <= -30.0) paused = true
        // Update openTrade dengan return unrealized untuk tampilan.
        if (inPos && openTrade != null) {
            val unreal = (valuationClose / entryPrice) * (1 - VIRTUAL_FEE) * (1 - VIRTUAL_FEE) - 1.0
            openTrade = openTrade.copy(returnFraction = unreal)
        }
    }
    val finalEquity = cash + if (inPos) btc * closed[n - 1].close else 0.0
    return RobotReplay(
        finalEquity = finalEquity,
        maxDrawdownPct = maxDd,
        trades = trades,
        openTrade = openTrade,
        breakerPaused = paused,
        breakerWarning = maxDd <= -25.0
    )
}

// Decisions: key = entrySignalTime, value =
//   "IKUT" | "LEWATI|<alasan>" | "PANIC|<harga panic>"
// Missing decision untuk trade Robot yang sudah tertutup = TERLEWAT.
// Keputusan hanya bisa dicatat untuk sinyal TERAKHIR yang sudah tutup
// (UI tidak menampilkan tombol untuk sinyal lama), jadi retroaktif tidak
// mungkin lewat jalur normal app. User replay meniru replay Robot persis,
// tapi hanya untuk trade yang diikuti, dengan kas/BTC sendiri.
fun replayUser(
    robot: RobotReplay,
    lastClosedClose: Double,
    decisions: Map<Long, String>
): UserReplay {
    var cash = VIRTUAL_CAPITAL
    var btc = 0.0
    var inPos = false
    val followed = ArrayList<VirtualTrade>()
    var skipped = 0
    var panicCount = 0
    var missedWinners = 0
    var missedDollars = 0.0
    var avoidedLosers = 0
    var avoidedDollars = 0.0
    var openTrade: VirtualTrade? = null
    val allRobotTrades = robot.trades + listOfNotNull(robot.openTrade)
    for (t in allRobotTrades) {
        val raw = decisions[t.entrySignalTime]
        when {
            raw == null || raw.startsWith("LEWATI") -> {
                skipped++
                if (!t.isOpen && t.returnFraction != null) {
                    if (t.returnFraction > 0) {
                        missedWinners++
                        missedDollars += cash * t.returnFraction
                    } else {
                        avoidedLosers++
                        avoidedDollars += cash * t.returnFraction
                    }
                }
            }
            raw.startsWith("PANIC|") -> {
                val panicPrice = raw.substringAfter("|").toDoubleOrNull()
                if (panicPrice != null && panicPrice > 0) {
                    panicCount++
                    val ret = (panicPrice / t.entryPrice) * (1 - VIRTUAL_FEE) * (1 - VIRTUAL_FEE) - 1.0
                    val invest = cash
                    cash = invest * (1 + ret)
                    followed.add(
                        t.copy(
                            exitPrice = panicPrice,
                            returnFraction = ret,
                            isOpen = false,
                            isPanic = true,
                            panicPrice = panicPrice
                        )
                    )
                } else {
                    skipped++
                }
            }
            raw == "IKUT" -> {
                if (t.isOpen) {
                    val invest = cash
                    btc = (invest * (1 - VIRTUAL_FEE)) / t.entryPrice
                    cash = 0.0
                    inPos = true
                    val unreal = (lastClosedClose / t.entryPrice) * (1 - VIRTUAL_FEE) * (1 - VIRTUAL_FEE) - 1.0
                    openTrade = t.copy(returnFraction = unreal)
                    followed.add(openTrade!!)
                } else if (t.returnFraction != null && t.exitPrice != null) {
                    val invest = cash
                    cash = invest * (1 + t.returnFraction)
                    followed.add(t)
                }
            }
            else -> skipped++
        }
    }
    val totalEntries = allRobotTrades.size
    val followedCount = followed.size
    val finalEquity = cash + if (inPos) btc * lastClosedClose else 0.0
    val followRate = if (totalEntries > 0) followedCount.toDouble() / totalEntries * 100.0 else 100.0
    return UserReplay(
        finalEquity = finalEquity,
        followedTrades = followed,
        followedCount = followedCount,
        skippedCount = skipped,
        panicCount = panicCount,
        missedWinnerCount = missedWinners,
        missedWinnerDollars = missedDollars,
        avoidedLoserCount = avoidedLosers,
        avoidedLoserDollars = avoidedDollars,
        followRatePct = followRate,
        openTrade = openTrade
    )
}

// Nilai buy-and-hold virtual: beli di open candle tutup pertama, nilai di
// close terakhir, fee entry 0,1% saja (belum dijual).
fun buyHoldValue(firstOpen: Double, lastClose: Double): Double {
    if (firstOpen <= 0) return VIRTUAL_CAPITAL
    return VIRTUAL_CAPITAL * (1 - VIRTUAL_FEE) * lastClose / firstOpen
}
