package com.daftech.sentinel

import com.daftech.sentinel.engine.Candle
import com.daftech.sentinel.engine.buyHoldValue
import com.daftech.sentinel.engine.checkHalt
import com.daftech.sentinel.engine.replayRobot
import com.daftech.sentinel.engine.replayUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Gate v2: replay virtual, cooldown, breaker, user ledger, halt.
// Candle helper: openTime naik 4 jam per candle.
private fun candles(closes: List<Double>, base: Long = 1_700_000_000_000L): List<Candle> {
    return closes.mapIndexed { i, c ->
        Candle(
            openTime = base + i * 4L * 60 * 60 * 1000,
            open = c,
            high = c + 1.0,
            low = c - 1.0,
            close = c,
            volume = 100.0
        )
    }
}

@Test
class VirtualEngineTest {

    @Test
    fun breakoutTradeExecutesAtNextOpenAndExitsAtDonchianLow() {
        // 60 candle flat 100, lalu breakout close 120, lalu breakdown close 90.
        val closes = MutableList(60) { 100.0 }
        closes.add(120.0) // index 60: breakout signal
        closes.add(121.0) // index 61: running? No — closed list, open 121 dipakai entry
        closes.add(90.0) // index 62: breakdown signal (close < Donchian low prior 20 = 99)
        closes.add(91.0) // index 63: exit execution open
        closes.add(91.0)
        val closed = candles(closes)
        val r = replayRobot(closed, null, false)
        // Entry signal index 60 -> eksekusi open index 61 = 121.
        // Exit signal index 62 -> eksekusi open index 63 = 91.
        assertEquals(1, r.trades.size)
        val t = r.trades[0]
        assertEquals(121.0, t.entryPrice, 0.001)
        assertEquals(91.0, t.exitPrice!!, 0.001)
        assertTrue(t.returnFraction!! < 0)
    }

    @Test
    fun cooldownSkipsEntriesAfterLoss() {
        // Trade loss pertama, lalu breakout lagi dalam 3 candle -> harus di-skip,
        // breakout setelah cooldown -> boleh masuk.
        val closes = MutableList(60) { 100.0 }
        closes.add(120.0) // 60 breakout
        closes.add(121.0) // 61 entry open
        closes.add(90.0) // 62 breakdown -> exit open 63
        closes.add(91.0) // 63 exit + juga bisa jadi candle berikutnya
        // Setelah exit signal 62, cooldown sampai signal index 65.
        // Buat breakout di 64 (dalam cooldown) dan di 66 (di luar cooldown).
        // Untuk breakout, close harus > high prior 20. High prior di 64 masih
        // 121 (dari candle 61), jadi close 130 di 64 adalah breakout.
        closes.add(130.0) // 64 breakout dalam cooldown -> skip
        closes.add(131.0) // 65 open (tidak entry)
        closes.add(140.0) // 66 breakout di luar cooldown (di atas high prior 132) -> entry open 67
        closes.add(131.0) // 67 entry
        closes.add(131.0) // 68
        val closed = candles(closes)
        val r = replayRobot(closed, null, false)
        // Hanya 2 trade: loss pertama + trade kedua (yang masih terbuka atau
        // tertutup tergantung data). Yang penting: tidak ada trade yang entry
        // signal-nya 64.
        val entryTimes = r.trades.map { it.entrySignalTime } + listOfNotNull(r.openTrade?.entrySignalTime)
        val cooldownEntryTime = closed[64].openTime
        assertTrue(!entryTimes.contains(cooldownEntryTime))
        assertTrue(r.trades.size + (if (r.openTrade != null) 1 else 0) >= 2)
    }

    @Test
    fun breakerPausesAfter30PercentDrawdown() {
        // Entry 120, exit 70 -> loss >30% -> DD <= -30% -> paused.
        val closes = MutableList(60) { 100.0 }
        closes.add(120.0) // 60 breakout
        closes.add(121.0) // 61 entry
        closes.add(70.0) // 62 breakdown
        closes.add(71.0) // 63 exit
        closes.add(71.0)
        val closed = candles(closes)
        val r = replayRobot(closed, null, false)
        assertTrue(r.maxDrawdownPct <= -30.0)
        assertTrue(r.breakerPaused)
    }

    @Test
    fun userLedgerFollowSkipPanicAndTerlewat() {
        val closes = MutableList(60) { 100.0 }
        closes.add(120.0)
        closes.add(121.0)
        closes.add(90.0)
        closes.add(91.0)
        closes.add(91.0)
        val closed = candles(closes)
        val robot = replayRobot(closed, null, false)
        assertEquals(1, robot.trades.size)
        val entryTime = robot.trades[0].entrySignalTime
        // Ikut -> ekuitas user sama dengan robot untuk trade ini.
        val uFollow = replayUser(robot, 91.0, mapOf(entryTime to "IKUT"))
        assertEquals(1, uFollow.followedCount)
        assertEquals(0, uFollow.skippedCount)
        assertEquals(robot.finalEquity, uFollow.finalEquity, 0.01)
        // Lewati -> skipped, ekuitas tetap 10000, missed/avoided tercatat.
        val uSkip = replayUser(robot, 91.0, mapOf(entryTime to "LEWATI|Ragu"))
        assertEquals(0, uSkip.followedCount)
        assertEquals(1, uSkip.skippedCount)
        assertEquals(10000.0, uSkip.finalEquity, 0.01)
        // Tanpa keputusan -> terlewat, sama seperti lewati untuk ekuitas.
        val uMiss = replayUser(robot, 91.0, emptyMap())
        assertEquals(1, uMiss.skippedCount)
        assertEquals(10000.0, uMiss.finalEquity, 0.01)
        // Panic di 100 -> return panic berbeda dari exit Robot (91).
        val uPanic = replayUser(robot, 91.0, mapOf(entryTime to "PANIC|100.0"))
        assertEquals(1, uPanic.panicCount)
        assertTrue(uPanic.finalEquity > robot.finalEquity)
    }

    @Test
    fun haltDetectsGapAndDuplicate() {
        val good = candles(MutableList(60) { 100.0 })
        assertNull(checkHalt(good))
        val gap = good.toMutableList()
        gap[10] = gap[10].copy(openTime = gap[10].openTime + 4L * 60 * 60 * 1000)
        assertNotNull(checkHalt(gap))
        val dup = good.toMutableList()
        dup[11] = dup[11].copy(openTime = dup[10].openTime)
        assertNotNull(checkHalt(dup))
        assertNotNull(checkHalt(candles(List(10) { 100.0 })))
    }

    @Test
    fun buyHoldValueMatchesFormula() {
        // Beli $10000*(1-0,001) di 100, nilai di 200 = ~19980.
        assertEquals(19980.0, buyHoldValue(100.0, 200.0), 0.01)
    }
}
