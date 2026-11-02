package com.daftech.sentinel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daftech.sentinel.engine.Candle
import com.daftech.sentinel.engine.RobotReplay
import com.daftech.sentinel.engine.SignalResult
import com.daftech.sentinel.engine.UserReplay
import com.daftech.sentinel.engine.PaperPosition
import com.daftech.sentinel.engine.PaperTrade
import com.daftech.sentinel.engine.buyHoldValue
import com.daftech.sentinel.engine.paperBuyCost
import com.daftech.sentinel.engine.paperBuyFill
import com.daftech.sentinel.engine.paperQtyByRisk
import com.daftech.sentinel.engine.paperSellFill
import com.daftech.sentinel.engine.paperSellProceeds
import com.daftech.sentinel.engine.paperTradeReturn
import com.daftech.sentinel.engine.paperUnrealizedFraction
import com.daftech.sentinel.engine.checkHalt
import com.daftech.sentinel.engine.computeSignal
import com.daftech.sentinel.engine.fetchCandles4h
import com.daftech.sentinel.engine.replayRobot
import com.daftech.sentinel.engine.replayUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SentinelMode { AGRESIF, AMAN }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var mode by mutableStateOf(SentinelMode.AMAN)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var signal by mutableStateOf<SignalResult?>(null)
        private set
    var lastUpdated by mutableStateOf("Belum pernah refresh")
        private set
    var selectedTab by mutableStateOf(0)
        private set

    var haltReason by mutableStateOf<String?>(null)
        private set
    var haltAcknowledged by mutableStateOf(false)
        private set
    var robotAgresif by mutableStateOf<RobotReplay?>(null)
        private set
    var robotAman by mutableStateOf<RobotReplay?>(null)
        private set
    var userAgresif by mutableStateOf<UserReplay?>(null)
        private set
    var userAman by mutableStateOf<UserReplay?>(null)
        private set
    var buyHoldEquity by mutableStateOf<Double?>(null)
        private set
    var windowStartLabel by mutableStateOf("")
        private set
    var windowCandleCount by mutableStateOf(0)
        private set
    var lastClosedClose by mutableStateOf<Double?>(null)
        private set
    var lastClosedTime by mutableStateOf<Long?>(null)
        private set
    var runningOpen by mutableStateOf<Double?>(null)
        private set
    var runningClose by mutableStateOf<Double?>(null)
        private set
    // --- Paper trading v3: LONG SPOT ONLY, PAPER ONLY. Bukan order nyata. ---
    var paperCash by mutableStateOf(10000.0)
        private set
    var paperPosition by mutableStateOf<PaperPosition?>(null)
        private set
    var paperTrades by mutableStateOf<List<PaperTrade>>(emptyList())
        private set
    var paperRiskPct by mutableStateOf(1.0)
        private set
    var paperNotice by mutableStateOf<String?>(null)
        private set
    var showLewatiReasons by mutableStateOf(false)
        private set
    var virtualNotice by mutableStateOf<String?>(null)
        private set

    private val prefs = app.getSharedPreferences("sentinel_v2_virtual", 0)
    private val paperPrefs = app.getSharedPreferences("sentinel_v3_paper", 0)

    init { loadPaper() }

    private fun loadDecisions(): MutableMap<String, String> {
        val map = HashMap<String, String>()
        try {
            val obj = JSONObject(prefs.getString("decisions", "{}") ?: "{}")
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun saveDecisions(map: Map<String, String>) {
        try {
            val obj = JSONObject()
            for ((k, v) in map) obj.put(k, v)
            prefs.edit().putString("decisions", obj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun decisionsForMode(m: SentinelMode): Map<Long, String> {
        val prefix = m.name + "|"
        val out = HashMap<Long, String>()
        for ((k, v) in loadDecisions()) {
            if (k.startsWith(prefix)) {
                k.substringAfter("|").toLongOrNull()?.let { out[it] = v }
            }
        }
        return out
    }

    fun currentDecision(): String? {
        val t = lastClosedTime ?: return null
        return decisionsForMode(mode)[t]
    }

    fun selectMode(m: SentinelMode) {
        mode = m
        showLewatiReasons = false
    }

    fun selectTab(t: Int) { selectedTab = t }

    fun acknowledgeHalt() { haltAcknowledged = true }

    fun toggleLewatiReasons() { showLewatiReasons = !showLewatiReasons }

    fun decideIkut() {
        val t = lastClosedTime ?: return
        val map = loadDecisions()
        map[mode.name + "|" + t] = "IKUT"
        saveDecisions(map)
        virtualNotice = "VIRTUAL: Ikut dicatat untuk sinyal ini. Tidak ada order nyata."
        showLewatiReasons = false
        recomputeUser()
    }

    fun decideLewati(reason: String) {
        val t = lastClosedTime ?: return
        val map = loadDecisions()
        map[mode.name + "|" + t] = "LEWATI|" + reason
        saveDecisions(map)
        virtualNotice = "VIRTUAL: Lewati ($reason) dicatat. Tidak ada order nyata."
        showLewatiReasons = false
        recomputeUser()
    }

    fun panicExit() {
        val user = if (mode == SentinelMode.AGRESIF) userAgresif else userAman
        val open = user?.openTrade ?: return
        val price = lastClosedClose ?: return
        val map = loadDecisions()
        map[mode.name + "|" + open.entrySignalTime] = "PANIC|" + price
        saveDecisions(map)
        virtualNotice = "VIRTUAL: Panic exit dicatat di close terakhir. Ledger Robot tidak berubah."
        recomputeUser()
    }

    fun resetVirtual() {
        prefs.edit().remove("decisions").apply()
        virtualNotice = "VIRTUAL: Riwayat keputusan user dihapus (reset). Ledger Robot dihitung ulang dari data."
        recomputeUser()
    }

    private fun recomputeUser() {
        val ra = robotAgresif
        val rm = robotAman
        val lc = lastClosedClose
        if (ra != null && rm != null && lc != null) {
            userAgresif = replayUser(ra, lc, decisionsForMode(SentinelMode.AGRESIF))
            userAman = replayUser(rm, lc, decisionsForMode(SentinelMode.AMAN))
        }
    }


    // ---------- Paper trading v3 ----------
    private fun loadPaper() {
        try {
            paperCash = paperPrefs.getString("cash", "10000.0")?.toDoubleOrNull() ?: 10000.0
            paperRiskPct = paperPrefs.getString("riskPct", "1.0")?.toDoubleOrNull() ?: 1.0
            val ep = paperPrefs.getString("posEntry", null)?.toDoubleOrNull()
            val qty = paperPrefs.getString("posQty", null)?.toDoubleOrNull()
            val stop = paperPrefs.getString("posStop", null)?.toDoubleOrNull()
            val label = paperPrefs.getString("posLabel", "") ?: ""
            paperPosition = if (ep != null && qty != null && stop != null) PaperPosition(ep, qty, stop, label) else null
            val tradesObj = JSONObject(paperPrefs.getString("trades", "[]") ?: "[]")
            val arr = tradesObj.optJSONArray("list")
            val list = ArrayList<PaperTrade>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(PaperTrade(o.getDouble("entry"), o.getDouble("exit"), o.getDouble("qty"), o.getDouble("ret"), o.optString("reason", "")))
                }
            }
            paperTrades = list
        } catch (_: Exception) {
            paperCash = 10000.0; paperPosition = null; paperTrades = emptyList()
        }
    }

    private fun savePaper() {
        try {
            val e = paperPrefs.edit()
            e.putString("cash", paperCash.toString())
            e.putString("riskPct", paperRiskPct.toString())
            val pos = paperPosition
            if (pos != null) {
                e.putString("posEntry", pos.entryFillPrice.toString())
                e.putString("posQty", pos.qty.toString())
                e.putString("posStop", pos.stopPrice.toString())
                e.putString("posLabel", pos.entryLabel)
            } else {
                e.remove("posEntry"); e.remove("posQty"); e.remove("posStop"); e.remove("posLabel")
            }
            val obj = JSONObject(); val arr = org.json.JSONArray()
            for (tr in paperTrades) {
                val o = JSONObject()
                o.put("entry", tr.entryFillPrice); o.put("exit", tr.exitFillPrice)
                o.put("qty", tr.qty); o.put("ret", tr.returnFraction); o.put("reason", tr.reason)
                arr.put(o)
            }
            obj.put("list", arr)
            e.putString("trades", obj.toString())
            e.apply()
        } catch (_: Exception) {
        }
    }

    fun selectPaperRiskPct(p: Double) { paperRiskPct = p; savePaper() }

    fun paperBuy() {
        val sig = signal ?: run { paperNotice = "PAPER: refresh sinyal dulu."; return }
        if (paperPosition != null) { paperNotice = "PAPER: sudah ada posisi terbuka. Jual dulu sebelum BUY baru (1 posisi)."; return }
        val action = if (mode == SentinelMode.AGRESIF) sig.aggressiveAction else sig.safeAction
        if (!action.startsWith("LONG")) { paperNotice = "PAPER: perintah sekarang bukan LONG (" + action + "), jadi tidak ada BUY paper."; return }
        val entryRef = runningOpen ?: sig.lastClose
        val qty = paperQtyByRisk(paperCash, entryRef, sig.donchianLow, paperRiskPct / 100.0)
        if (qty <= 0.0) { paperNotice = "PAPER: ukuran posisi 0 — stop Donchian low harus di bawah harga entry."; return }
        val fill = paperBuyFill(entryRef)
        val cost = paperBuyCost(qty, fill)
        if (cost > paperCash) { paperNotice = "PAPER: cash tidak cukup untuk ukuran posisi ini."; return }
        paperCash -= cost
        paperPosition = PaperPosition(fill, qty, sig.donchianLow, "BUY paper")
        paperNotice = "PAPER BUY: " + qty.toString() + " BTC di fill " + fill.toString() + " (acuan " + entryRef.toString() + ", fee+slippage sudah dipotong). BUKAN order nyata."
        savePaper()
    }

    fun paperSellManual() {
        val pos = paperPosition ?: run { paperNotice = "PAPER: tidak ada posisi terbuka untuk dijual."; return }
        val exitRef = runningClose ?: lastClosedClose ?: run { paperNotice = "PAPER: tidak ada harga acuan untuk SELL."; return }
        val fill = paperSellFill(exitRef)
        val proceeds = paperSellProceeds(pos.qty, fill)
        paperCash += proceeds
        val ret = paperTradeReturn(pos.entryFillPrice, fill)
        paperTrades = paperTrades + PaperTrade(pos.entryFillPrice, fill, pos.qty, ret, "SELL manual")
        paperPosition = null
        paperNotice = "PAPER SELL manual: keluar di fill " + fill.toString() + ", return " + ret.toString() + ". BUKAN order nyata."
        savePaper()
    }

    private fun paperAutoExit(sig: SignalResult, runOpen: Double?) {
        val pos = paperPosition ?: return
        val action = sig.aggressiveAction
        if (action != "FLAT / EXIT") return
        val exitRef = runOpen ?: return
        val fill = paperSellFill(exitRef)
        val proceeds = paperSellProceeds(pos.qty, fill)
        paperCash += proceeds
        val ret = paperTradeReturn(pos.entryFillPrice, fill)
        paperTrades = paperTrades + PaperTrade(pos.entryFillPrice, fill, pos.qty, ret, "EXIT Donchian low (otomatis)")
        paperPosition = null
        paperNotice = "PAPER EXIT otomatis: close tembus Donchian low, posisi ditutup di open berikutnya (fill " + fill.toString() + "). BUKAN order nyata."
        savePaper()
    }

    fun resetPaper() {
        paperCash = 10000.0; paperPosition = null; paperTrades = emptyList()
        paperNotice = "PAPER: saldo di-reset ke \$10.000 virtual, posisi dan riwayat paper dihapus."
        savePaper()
    }

    fun paperUnrealized(): Double? {
        val pos = paperPosition ?: return null
        val ref = runningClose ?: lastClosedClose ?: return null
        return paperUnrealizedFraction(pos.entryFillPrice, ref)
    }

    fun refreshData() {
        if (isLoading) return
        isLoading = true
        error = null
        haltAcknowledged = false
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val candles = fetchCandles4h()
                    val sig = computeSignal(candles)
                    val closed: List<Candle> = if (candles.size >= 2) candles.dropLast(1) else candles
                    val runOpen: Double? = if (candles.size >= 2) candles.last().open else null
                    val runClose: Double? = if (candles.size >= 2) candles.last().close else null
                    RefreshPaper(sig, closed, runOpen, runClose)
                }
                val sig = result.sig
                val closed = result.closed
                val runOpen = result.runOpen
                signal = sig
                runningClose = result.runClose
                lastClosedClose = closed.lastOrNull()?.close
                lastClosedTime = closed.lastOrNull()?.openTime
                runningOpen = runOpen
                windowCandleCount = closed.size
                val fmt = SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID"))
                fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                windowStartLabel = if (closed.isNotEmpty()) fmt.format(Date(closed.first().openTime)) + " WIB" else ""
                val halt = checkHalt(closed)
                haltReason = halt
                if (halt == null) {
                    robotAgresif = replayRobot(closed, runOpen, false)
                    robotAman = replayRobot(closed, runOpen, true)
                    buyHoldEquity = if (closed.size >= 2) {
                        buyHoldValue(closed.first().open, closed.last().close)
                    } else null
                    recomputeUser()
                } else {
                    robotAgresif = null
                    robotAman = null
                    userAgresif = null
                    userAman = null
                    buyHoldEquity = null
                }
                if (halt == null) paperAutoExit(sig, runOpen)
                val fmtFull = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID"))
                fmtFull.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                lastUpdated = "Update: " + fmtFull.format(Date()) + " WIB"
            } catch (e: Exception) {
                error = "Gagal tarik data: " + (e.message ?: "tidak diketahui")
            } finally {
                isLoading = false
            }
        }
    }
}

private data class RefreshPaper(val sig: SignalResult, val closed: List<Candle>, val runOpen: Double?, val runClose: Double?)
