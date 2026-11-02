package com.daftech.sentinel.engine

import kotlin.math.min

// SENTINEL v3 — Paper Trading, LONG SPOT ONLY, PAPER ONLY.
// Pure Kotlin, tanpa dependensi Android, supaya bisa di-unit-test.
// BUKAN eksekusi nyata: tidak ada API key, tidak ada order ke exchange,
// tidak ada short, tidak ada leverage, tidak ada futures.
// Satu posisi long spot paper. SELL di sini HANYA berarti jual posisi long
// yang sedang dipegang (exit), bukan short.
// Fill paper meniru order market: fee 0,1% per sisi (sama dengan backtest)
// + slippage merugikan 0,05% per sisi, supaya hasil paper tidak terlalu
// optimistis dibanding eksekusi nyata nanti.

const val PAPER_FEE = 0.001
const val PAPER_SLIPPAGE = 0.0005
const val PAPER_START_CASH = 10000.0

data class PaperPosition(
    val entryFillPrice: Double,
    val qty: Double,
    val stopPrice: Double,
    val entryLabel: String
)

data class PaperTrade(
    val entryFillPrice: Double,
    val exitFillPrice: Double,
    val qty: Double,
    val returnFraction: Double,
    val reason: String
)

// Harga isi BUY: harga acuan + slippage (beli lebih mahal).
fun paperBuyFill(refPrice: Double): Double = refPrice * (1.0 + PAPER_SLIPPAGE)

// Harga isi SELL: harga acuan - slippage (jual lebih murah).
fun paperSellFill(refPrice: Double): Double = refPrice * (1.0 - PAPER_SLIPPAGE)

// Qty kalau pakai seluruh cash: biaya total = qty*fill*(1+fee) <= cash.
fun paperQtyForCash(cash: Double, fillPrice: Double): Double {
    if (cash <= 0.0 || fillPrice <= 0.0) return 0.0
    return cash / (fillPrice * (1.0 + PAPER_FEE))
}

// Qty dari risiko: rugi maks = qty * (entryFill - stop). Qty dibatasi cash.
fun paperQtyByRisk(cash: Double, entryRefPrice: Double, stopPrice: Double, riskFraction: Double): Double {
    val fill = paperBuyFill(entryRefPrice)
    val perUnitRisk = fill - stopPrice
    if (cash <= 0.0 || perUnitRisk <= 0.0 || riskFraction <= 0.0) return 0.0
    val qtyByRisk = (cash * riskFraction) / perUnitRisk
    return min(qtyByRisk, paperQtyForCash(cash, fill))
}

// Dana yang keluar dari cash saat BUY (harga isi + fee).
fun paperBuyCost(qty: Double, fillPrice: Double): Double = qty * fillPrice * (1.0 + PAPER_FEE)

// Dana yang kembali ke cash saat SELL (harga isi - fee).
fun paperSellProceeds(qty: Double, fillPrice: Double): Double = qty * fillPrice * (1.0 - PAPER_FEE)

// Return trade paper, sudah termasuk fee dan slippage kedua sisi.
fun paperTradeReturn(entryFill: Double, exitFill: Double): Double {
    if (entryFill <= 0.0) return 0.0
    val costPerUnit = entryFill * (1.0 + PAPER_FEE)
    val proceedsPerUnit = exitFill * (1.0 - PAPER_FEE)
    return (proceedsPerUnit - costPerUnit) / costPerUnit
}

// Unrealized posisi long paper terhadap harga acuan sekarang.
fun paperUnrealizedFraction(entryFill: Double, currentRefPrice: Double): Double {
    if (entryFill <= 0.0) return 0.0
    return (paperSellFill(currentRefPrice) * (1.0 - PAPER_FEE) - entryFill * (1.0 + PAPER_FEE)) /
        (entryFill * (1.0 + PAPER_FEE))
}
