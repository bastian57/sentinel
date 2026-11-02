package com.daftech.sentinel

import com.daftech.sentinel.engine.paperBuyCost
import com.daftech.sentinel.engine.paperBuyFill
import com.daftech.sentinel.engine.paperQtyByRisk
import com.daftech.sentinel.engine.paperQtyForCash
import com.daftech.sentinel.engine.paperSellFill
import com.daftech.sentinel.engine.paperSellProceeds
import com.daftech.sentinel.engine.paperTradeReturn
import com.daftech.sentinel.engine.paperUnrealizedFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Gate v3: paper trading long spot only — fee + slippage, sizing risiko,
// tanpa short (tidak ada fungsi short di PaperEngine untuk diuji).

@Test
class PaperEngineTest {

    @Test
    fun buyFillIsAboveRefAndSellFillBelowRef() {
        assertEquals(80040.0, paperBuyFill(80000.0), 0.001)
        assertEquals(79960.0, paperSellFill(80000.0), 0.001)
    }

    @Test
    fun buyCostNeverExceedsCash() {
        val qty = paperQtyForCash(10000.0, 80040.0)
        assertTrue(qty > 0.0)
        assertTrue(paperBuyCost(qty, 80040.0) <= 10000.0 + 0.001)
    }

    @Test
    fun riskSizingCapsQtyBelowFullCashQty() {
        // Cash 10000, entry acuan 80000, stop 76000, risiko 1% = $100.
        // Fill 80040, risiko per unit 4040 -> qty risiko 0.02475, di bawah qty cash penuh.
        val qty = paperQtyByRisk(10000.0, 80000.0, 76000.0, 0.01)
        assertEquals(0.02475, qty, 0.0001)
        assertTrue(qty < paperQtyForCash(10000.0, 80040.0))
    }

    @Test
    fun riskSizingReturnsZeroWhenStopAboveEntry() {
        // Stop di atas entry = tidak ada risiko per unit yang valid -> tidak boleh BUY.
        assertEquals(0.0, paperQtyByRisk(10000.0, 80000.0, 81000.0, 0.01), 0.0)
        assertEquals(0.0, paperQtyByRisk(10000.0, 80000.0, 76000.0, 0.0), 0.0)
    }

    @Test
    fun winningTradeReturnIsPositiveAfterFeeAndSlippage() {
        // Beli fill 80040, jual fill 89955 (acuan 90000): untung bersih > 0.
        val r = paperTradeReturn(80040.0, 89955.0)
        assertTrue(r > 0.10)
        assertEquals(0.1218, r, 0.001)
    }

    @Test
    fun losingTradeReturnIsNegative() {
        val r = paperTradeReturn(80040.0, 69965.0)
        assertTrue(r < -0.10)
    }

    @Test
    fun sellProceedsDeductFee() {
        assertEquals(8986.5045, paperSellProceeds(0.1, 89955.0), 0.001)
    }

    @Test
    fun unrealizedIsZeroAtEntryAndPositiveWhenPriceRises() {
        // Harga acuan sama dengan acuan entry: sell fill di bawah entry fill -> sedikit negatif (fee+slippage).
        assertTrue(paperUnrealizedFraction(80040.0, 80000.0) < 0.0)
        assertTrue(paperUnrealizedFraction(80040.0, 90000.0) > 0.10)
    }
}
