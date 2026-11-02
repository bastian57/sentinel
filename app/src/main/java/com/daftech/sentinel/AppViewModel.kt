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
import com.daftech.sentinel.engine.buyHoldValue
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
    var showLewatiReasons by mutableStateOf(false)
        private set
    var virtualNotice by mutableStateOf<String?>(null)
        private set

    private val prefs = app.getSharedPreferences("sentinel_v2_virtual", 0)

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
                    Triple(sig, closed, runOpen)
                }
                val sig = result.first
                val closed = result.second
                val runOpen = result.third
                signal = sig
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
