package com.daftech.sentinel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daftech.sentinel.engine.SignalResult
import com.daftech.sentinel.engine.computeSignal
import com.daftech.sentinel.engine.fetchCandles4h
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SentinelMode { AGRESIF, AMAN }

class AppViewModel : ViewModel {
    var mode by mutableStateOf(SentinelMode.AMAN)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var signal by mutableStateOf<SignalResult?>(null)
        private set
    var lastUpdated by mutableStateOf<String>("Belum pernah refresh")
        private set
    var selectedTab by mutableStateOf(0)
        private set

    fun selectMode(m: SentinelMode) { mode = m }
    fun selectTab(t: Int) { selectedTab = t }

    fun refreshData() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val candles = fetchCandles4h()
                    computeSignal(candles)
                }
                signal = result
                val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID"))
                fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                lastUpdated = "Update: " + fmt.format(Date()) + " WIB"
            } catch (e: Exception) {
                error = "Gagal tarik data: " + (e.message ?: "tidak diketahui")
            } finally {
                isLoading = false
            }
        }
    }
}
