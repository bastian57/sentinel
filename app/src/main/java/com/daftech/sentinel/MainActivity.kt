package com.daftech.sentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daftech.sentinel.engine.paperBuyCost
import com.daftech.sentinel.engine.paperBuyFill
import com.daftech.sentinel.engine.paperQtyByRisk
import com.daftech.sentinel.engine.VirtualTrade
import com.daftech.sentinel.ui.SentinelTheme
import com.daftech.sentinel.ui.SnCyan
import com.daftech.sentinel.ui.SnGold
import com.daftech.sentinel.ui.SnGreen
import com.daftech.sentinel.ui.SnRed
import com.daftech.sentinel.ui.SnSurface
import com.daftech.sentinel.ui.SnText
import com.daftech.sentinel.ui.SnTextDim
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelTheme {
                SentinelScreen()
            }
        }
    }
}

private fun fmtUsd(v: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 2
    return "$" + nf.format(v)
}

private fun fmtPct(v: Double): String {
    return String.format(Locale.US, "%+.2f%%", v)
}

private fun fmtTime(ms: Long): String {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID"))
    fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    return fmt.format(Date(ms)) + " WIB"
}

@Composable
private fun RowLine(label: String, value: String, valueColor: Color = SnText) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SnTextDim, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ModeToggle(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val modes = listOf(
            SentinelMode.AGRESIF to "Mode Agresif",
            SentinelMode.AMAN to "Mode Aman"
        )
        modes.forEach { (m, label) ->
            val selected = vm.mode == m
            if (selected) {
                Button(
                    onClick = { vm.selectMode(m) },
                    colors = ButtonDefaults.buttonColors(containerColor = SnGold, contentColor = Color(0xFF1A1200)),
                    modifier = Modifier.weight(1f)
                ) { Text(label, fontSize = 13.sp) }
            } else {
                OutlinedButton(
                    onClick = { vm.selectMode(m) },
                    modifier = Modifier.weight(1f)
                ) { Text(label, color = SnText, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun TradeLine(t: VirtualTrade) {
    val ret = t.returnFraction
    val retText = if (t.isOpen) {
        "TERBUKA, unreal " + if (ret != null) fmtPct(ret * 100) else "-"
    } else if (ret != null) {
        fmtPct(ret * 100) + if (t.isPanic) " (PANIC)" else ""
    } else "-"
    val c = when {
        t.isOpen -> SnGold
        ret != null && ret > 0 -> SnGreen
        else -> SnRed
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(t.entrySignalTime), color = SnTextDim, fontSize = 12.sp)
            Text(retText, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Entry " + fmtUsd(t.entryPrice) + if (t.exitPrice != null) "  ->  Exit " + fmtUsd(t.exitPrice) else "",
            color = SnTextDim, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SentinelScreen(vm: AppViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        if (vm.signal == null) vm.refreshData()
    }
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)
    ) {
        Text("SENTINEL v3", color = SnGold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "BTC/USDT  •  4 jam  •  Spot long only  •  PAPER ONLY",
            color = SnTextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "PERINTAH + PAPER TRADING — tab Sinyal sekarang memberi perintah BUY / SELL-exit / TUNGGU dan mengeksekusi PAPER (virtual) di dalam app. App ini BELUM trading uang asli, BELUM testnet, TIDAK kirim order nyata, TIDAK minta API key, TANPA short/leverage/futures.",
            color = SnCyan, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Sinyal", "Virtual", "Disiplin", "Backtest", "Tentang").forEachIndexed { idx, title ->
                val selected = vm.selectedTab == idx
                if (selected) {
                    Button(
                        onClick = { vm.selectTab(idx) },
                        colors = ButtonDefaults.buttonColors(containerColor = SnGold, contentColor = Color(0xFF1A1200)),
                        modifier = Modifier.weight(1f)
                    ) { Text(title, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                } else {
                    OutlinedButton(
                        onClick = { vm.selectTab(idx) },
                        modifier = Modifier.weight(1f)
                    ) { Text(title, color = SnText, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        vm.haltReason?.let { halt ->
            if (!vm.haltAcknowledged) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1A1A))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("HALT VIRTUAL — tidak ada sinyal baru", color = SnRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(halt, color = SnText, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.acknowledgeHalt() }) {
                            Text("Saya mengerti (Lanjutkan)", fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        when (vm.selectedTab) {
            0 -> SignalTab(vm)
            1 -> VirtualTab(vm)
            2 -> DisciplineTab(vm)
            3 -> BacktestTab()
            4 -> AboutTab()
        }
        Spacer(Modifier.height(16.dp))
        Text("SENTINEL v3.0.0  •  by DAFTECH  •  Paper only, long spot only, no short", color = SnTextDim, fontSize = 12.sp)
    }
}

@Composable
private fun SignalTab(vm: AppViewModel) {
    ModeToggle(vm)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.refreshData() },
        enabled = !vm.isLoading,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SnGold, contentColor = Color(0xFF1A1200))
    ) {
        Text(if (vm.isLoading) "Menarik data..." else "Refresh sinyal", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    Text(vm.lastUpdated, color = SnTextDim, fontSize = 12.sp)
    vm.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = SnRed, fontSize = 13.sp)
    }
    vm.virtualNotice?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = SnCyan, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    val s = vm.signal
    if (s != null) {
        val action = if (vm.mode == SentinelMode.AGRESIF) s.aggressiveAction else s.safeAction
        val reason = if (vm.mode == SentinelMode.AGRESIF) s.aggressiveReason else s.safeReason
        val actionColor = when {
            action.startsWith("LONG") -> SnGreen
            action.startsWith("FLAT") -> SnRed
            else -> SnGold
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnSurface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Sinyal saat ini (VIRTUAL)", color = SnTextDim, fontSize = 13.sp)
                Text(action, color = actionColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(reason, color = SnText, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Mode Agresif = G murni. Mode Aman = G + ADX di atas 25 dan naik. Kalau Aman bilang SKIP, itu artinya setup ada tapi kualitasnya rendah.",
                    color = SnTextDim, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Keputusan user — hanya untuk sinyal terakhir yang sudah tutup.
        if (action.startsWith("LONG")) {
            val dec = vm.currentDecision()
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SnSurface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Keputusan VIRTUAL lo untuk sinyal ini", color = SnGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (dec == null) {
                        Text(
                            "Ketuk Ikut kalau lo mau simulasi entry virtual di open candle berikutnya. Kalau tidak diketuk sampai candle berikutnya tutup, trade ini dicatat Terlewat dan TIDAK bisa diambil retroaktif.",
                            color = SnTextDim, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.decideIkut() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SnGreen, contentColor = Color(0xFF062A1A))
                            ) { Text("Ikut (virtual)") }
                            OutlinedButton(
                                onClick = { vm.toggleLewatiReasons() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Lewati", color = SnText) }
                        }
                        if (vm.showLewatiReasons) {
                            Spacer(Modifier.height(10.dp))
                            Text("Alasan lewati:", color = SnTextDim, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Takut", "Ragu", "Ketinggalan", "Tidak setuju").forEach { r ->
                                    OutlinedButton(onClick = { vm.decideLewati(r) }) {
                                        Text(r, color = SnText, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Sudah dicatat: $dec", color = SnText, fontSize = 14.sp)
                        Text(
                            "Keputusan ini hanya untuk simulasi virtual di tab Virtual/Disiplin. Tidak ada order nyata yang dikirim.",
                            color = SnTextDim, fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnSurface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Indikator (candle terakhir yang sudah tutup)", color = SnTextDim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                RowLine("Harga close", fmtUsd(s.lastClose))
                RowLine("Candle tutup", fmtTime(s.lastOpenTime + 4 * 60 * 60 * 1000 - 1))
                RowLine("MA20 (4h)", fmtUsd(s.ma20))
                RowLine("MA50 (4h)", fmtUsd(s.ma50))
                RowLine("Tren MA", if (s.isUptrend) "NAIK (MA20 > MA50)" else "TIDAK NAIK", if (s.isUptrend) SnGreen else SnRed)
                RowLine("Donchian high 20", fmtUsd(s.donchianHigh))
                RowLine("Donchian low 20", fmtUsd(s.donchianLow))
                RowLine("Breakout?", if (s.isBreakout) "YA" else "TIDAK", if (s.isBreakout) SnGreen else SnText)
                val adxText = String.format(Locale.US, "%.1f", s.adx) + if (s.adxRising) " (naik)" else " (tidak naik)"
                RowLine("ADX14", adxText, if (s.adx > 25 && s.adxRising) SnGreen else SnGold)
                RowLine("Jumlah candle dipakai", s.candleCount.toString())
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Catatan: sinyal dihitung dari candle yang sudah tutup, bukan candle berjalan, supaya tidak repaint. Sinyal ini bukan ramalan akurat dan bukan saran beli/jual.",
            color = SnTextDim, fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        PaperDesk(vm)
    } else if (!vm.isLoading) {
        Text("Belum ada data. Tekan Refresh sinyal.", color = SnTextDim, fontSize = 14.sp)
    }
}

@Composable
private fun VirtualTab(vm: AppViewModel) {
    ModeToggle(vm)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.refreshData() },
        enabled = !vm.isLoading,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SnGold, contentColor = Color(0xFF1A1200))
    ) {
        Text(if (vm.isLoading) "Menarik data..." else "Refresh virtual", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    Text(vm.lastUpdated, color = SnTextDim, fontSize = 12.sp)
    vm.virtualNotice?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = SnCyan, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    val robot = if (vm.mode == SentinelMode.AGRESIF) vm.robotAgresif else vm.robotAman
    val user = if (vm.mode == SentinelMode.AGRESIF) vm.userAgresif else vm.userAman
    if (robot == null || user == null) {
        Text(
            "Belum ada portfolio virtual. Tekan Refresh virtual dulu. Kalau ada banner HALT, data belum lengkap dan tidak ada sinyal baru.",
            color = SnTextDim, fontSize = 14.sp
        )
        return
    }
    val diff = robot.finalEquity - user.finalEquity
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Portfolio VIRTUAL — window " + vm.windowCandleCount + " candle terakhir", color = SnGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Mulai window: " + vm.windowStartLabel, color = SnTextDim, fontSize = 12.sp)
            Text("Ini BUKAN sejak install app, tapi replay atas data yang ditarik (250 candle).", color = SnTextDim, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            RowLine("Robot (ikuti sempurna)", fmtUsd(robot.finalEquity), if (robot.finalEquity >= 10000.0) SnGreen else SnRed)
            RowLine("User (ikut yang lo ketuk)", fmtUsd(user.finalEquity), if (user.finalEquity >= 10000.0) SnGreen else SnRed)
            RowLine("Selisih Robot - User (biaya perilaku)", fmtUsd(diff), if (diff >= 0) SnGold else SnGreen)
            RowLine("DD Robot (dari puncak)", fmtPct(robot.maxDrawdownPct), SnRed)
            if (robot.breakerPaused) {
                Text("PAUSE VIRTUAL: DD Robot sudah lewat -30%. Tidak ada entry baru sampai resume manual (refresh tidak me-reset pause di window ini).", color = SnRed, fontSize = 13.sp)
            } else if (robot.breakerWarning) {
                Text("WARNING VIRTUAL: DD Robot sudah lewat -25%.", color = SnGold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            RowLine("Trade Robot tertutup", robot.trades.size.toString())
            RowLine("Trade Robot terbuka", if (robot.openTrade != null) "YA, entry " + fmtUsd(robot.openTrade.entryPrice) else "TIDAK")
        }
    }
    Spacer(Modifier.height(12.dp))
    val openUser = user.openTrade
    if (openUser != null) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnSurface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Posisi VIRTUAL lo yang masih terbuka", color = SnGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                RowLine("Entry virtual", fmtUsd(openUser.entryPrice))
                val unreal = openUser.returnFraction
                if (unreal != null) RowLine("Unrealized", fmtPct(unreal * 100), if (unreal >= 0) SnGreen else SnRed)
                Text(
                    "Hasil akhir BELUM DIKETAHUI — trade belum exit Donchian low. Panic exit di bawah ini menutup virtual di close terakhir, bukan prediksi.",
                    color = SnTextDim, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.panicExit() },
                    colors = ButtonDefaults.buttonColors(containerColor = SnRed)
                ) { Text("Tutup virtual sekarang (panic)", fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Counterfactual VIRTUAL — 3 kurva utuh, modal awal sama \$10.000", color = SnGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val bh = vm.buyHoldEquity
            if (bh != null) RowLine("Buy-and-hold (tidak trading)", fmtUsd(bh))
            val ra = vm.robotAgresif
            val rm = vm.robotAman
            if (ra != null) RowLine("G Agresif (Robot)", fmtUsd(ra.finalEquity))
            if (rm != null) RowLine("G+ADX Aman (Robot)", fmtUsd(rm.finalEquity))
            Text(
                "Kurva G adalah counterfactual yang sah untuk trade yang di-SKIP ADX, karena G dan Aman adalah dua portfolio lengkap yang jalurnya konsisten. Bukan daftar trade hipotetis per candle.",
                color = SnTextDim, fontSize = 12.sp
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Trade Robot terakhir (maks 10)", color = SnGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val all = robot.trades + listOfNotNull(robot.openTrade)
            if (all.isEmpty()) {
                Text("Belum ada trade Robot di window ini.", color = SnTextDim, fontSize = 13.sp)
            } else {
                all.takeLast(10).reversed().forEach { TradeLine(it) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { vm.resetVirtual() }, modifier = Modifier.fillMaxWidth()) {
        Text("Reset riwayat keputusan VIRTUAL", color = SnText, fontSize = 13.sp)
    }
}

@Composable
private fun DisciplineTab(vm: AppViewModel) {
    ModeToggle(vm)
    Spacer(Modifier.height(12.dp))
    val robot = if (vm.mode == SentinelMode.AGRESIF) vm.robotAgresif else vm.robotAman
    val user = if (vm.mode == SentinelMode.AGRESIF) vm.userAgresif else vm.userAman
    if (robot == null || user == null) {
        Text("Belum ada data disiplin. Tekan Refresh di tab Sinyal/Virtual dulu.", color = SnTextDim, fontSize = 14.sp)
        return
    }
    val diff = robot.finalEquity - user.finalEquity
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Disiplin VIRTUAL — angka saja, bukan nilai moral", color = SnGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            RowLine("Follow rate", String.format(Locale.US, "%.1f%%", user.followRatePct))
            RowLine("Trade diikuti", user.followedCount.toString())
            RowLine("Trade dilewati / terlewat", user.skippedCount.toString())
            RowLine("Panic exit", user.panicCount.toString())
            RowLine("Selisih ekuitas Robot - User", fmtUsd(diff), if (diff >= 0) SnGold else SnGreen)
            Spacer(Modifier.height(6.dp))
            RowLine("Missed winner (trade bagus yang dilewati)", user.missedWinnerCount.toString())
            RowLine("Nilai missed winner (virtual)", fmtUsd(user.missedWinnerDollars))
            RowLine("Avoided loser (trade rugi yang dihindari)", user.avoidedLoserCount.toString())
            RowLine("Nilai avoided loser (virtual)", fmtUsd(user.avoidedLoserDollars))
            Spacer(Modifier.height(6.dp))
            Text(
                "Missed winner dihitung SETELAH trade Robot exit, bukan saat dilewati. Jadi angka ini tidak mengubah ekuitas User, hanya menunjukkan biaya/keuntungan melewati trade.",
                color = SnTextDim, fontSize = 12.sp
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    val openUser = user.openTrade
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Kalkulator panic (VIRTUAL)", color = SnGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (openUser != null) {
                RowLine("Entry virtual", fmtUsd(openUser.entryPrice))
                val unreal = openUser.returnFraction
                if (unreal != null) RowLine("Unrealized sekarang", fmtPct(unreal * 100), if (unreal >= 0) SnGreen else SnRed)
                Text(
                    "Kalau lo panic-sell sekarang, loss/gain yang terkunci adalah unrealized di atas. Hasil akhir kalau mengikuti exit Donchian low BELUM DIKETAHUI — trade belum exit. App ini tidak memprediksi hasil akhirnya.",
                    color = SnText, fontSize = 13.sp
                )
            } else {
                Text(
                    "Tidak ada posisi virtual terbuka di mode ini. Kalkulator panic hanya bermakna saat ada posisi terbuka.",
                    color = SnTextDim, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ilustrasi historis (bukan janji): G out-of-sample DD -26,0% dengan return +189% kalau diikuti penuh, vs buy-and-hold DD -53,4% dengan return +389%. Menjual buy-and-hold di DD -30% mengunci loss -30% dan kehilangan rebound setelahnya — itu biaya panic yang diilustrasikan, bukan hitungan pasti untuk trade lo sekarang.",
                color = SnTextDim, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun BacktestTab() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Backtest Fase 1-3 (paper, fee 0,1%/sisi)", color = SnGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Fase 1, 4h, modal hipotetis \$10.000:", color = SnText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• Donchian20: full +1.755%, out-of-sample +266%, drawdown out -42,5%, profit factor out 2,07.", color = SnText, fontSize = 13.sp)
            Text("• Buy-and-hold pembanding: full +1.798%, out +389%, drawdown out -53,4%.", color = SnText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Fase 2, pemenang G (Donchian + MA filter):", color = SnText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• 4h in +997% (drawdown -51,9%), out +189% (drawdown -26,0%, profit factor 2,25), full +3.072%.", color = SnText, fontSize = 13.sp)
            Text("• 1h G: in +107,7%, out +48,6% — jauh lebih lemah, jadi basis tetap 4h.", color = SnText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Fase 3, filter timing ADX:", color = SnText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• G + ADX naik: 4h out +90% (drawdown -19,6%, profit factor 2,95), 1h out +61% (drawdown -19,9%).", color = SnText, fontSize = 13.sp)
            Text("• Konfirmasi candle, volume, pullback, squeeze, time-stop, dan exit MA20 semuanya gagal atau inkonsisten sebagai aturan utama.", color = SnText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Program otonom 2 Nov 2026 (V3, Kronos vol, sintetis):", color = SnText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• V3 skip ATR ekstrem, Kronos volatilitas, dan pabrik sintetis Kronos semuanya GUGUR di kill gate. Tidak ada filter baru di v2.", color = SnText, fontSize = 13.sp)
            Text("• v2 memakai HANYA G dan Aman yang sudah ada. Itu hasil yang sah, bukan kegagalan.", color = SnText, fontSize = 13.sp)
            Text("• Short cermin G (2 Nov 2026, 4h, 1x, fee 0,1%): in -80,5% PF 0,70, out -40,9% PF 0,74 — GAGAL, jadi v3 tidak punya tombol short.", color = SnText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Peringatan: angka full pemenang kena winner's curse karena dipilih dari banyak varian. Pakai angka out-of-sample sebagai ekspektasi jujur, bukan angka full.",
                color = SnRed, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AboutTab() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Tentang SENTINEL v3.0.0 — Perintah + Paper Trading", color = SnGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("• Pasangan: BTC/USDT spot saja.", color = SnText, fontSize = 13.sp)
            Text("• Timeframe: 4 jam.", color = SnText, fontSize = 13.sp)
            Text("• Tanpa leverage, tanpa futures, tanpa short.", color = SnText, fontSize = 13.sp)
            Text("• Paper trading v3: BUY/SELL paper di dalam app, fee 0,1% + slippage 0,05% per sisi. BUKAN order nyata.", color = SnText, fontSize = 13.sp)
            Text("• Testnet dan real BELUM ada di build ini — tahap berikutnya, real butuh gas terpisah lagi.", color = SnText, fontSize = 13.sp)
            Text("• TANPA short/futures/leverage. Riset short cermin G GUGUR (out -40,9%, PF 0,74) dan tidak dipakai.", color = SnText, fontSize = 13.sp)
            Text("• Data: Binance Vision (publik) dengan cadangan Binance US.", color = SnText, fontSize = 13.sp)
            Text("• Sinyal: Donchian20 + MA20>MA50, exit Donchian low. Mode Aman tambah ADX14 > 25 dan naik.", color = SnText, fontSize = 13.sp)
            Text("• v2 TIDAK memakai V3 (skip ATR) dan TIDAK memakai Kronos — keduanya gugur di pengujian otonom 2 Nov 2026.", color = SnText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Fakta yang tidak disembunyikan: G kalah return dari buy-and-hold di out-of-sample (+189% vs +389%) dan hanya menang di drawdown (-26,0% vs -53,4%). v2 mengukur apakah lo bisa mengikuti aturan itu tanpa panic-sell, bukan menjanjikan return lebih tinggi.",
                color = SnText, fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Risiko: backtest bukan jaminan masa depan. Drawdown historis masih puluhan persen. Jangan pakai uang yang tidak siap hilang, dan jangan jadikan sinyal ini satu-satunya dasar keputusan.",
                color = SnRed, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PaperDesk(vm: AppViewModel) {
    val s = vm.signal
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PERINTAH TRADING — PAPER ONLY (spot long)", color = SnGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("SELL di sini = jual posisi long paper yang sedang dipegang. TIDAK ADA short.", color = SnTextDim, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            val pos = vm.paperPosition
            val action = if (s != null) (if (vm.mode == SentinelMode.AGRESIF) s.aggressiveAction else s.safeAction) else "-"
            val command = when {
                pos != null -> "PEGANG — SELL saat EXIT Donchian low atau SELL manual"
                s != null && action.startsWith("LONG") -> "BUY (paper)"
                s != null && action.startsWith("FLAT") -> "SELL-EXIT (tidak ada posisi paper, jadi tidak ada yang dijual)"
                else -> "TUNGGU — tidak ada BUY/SELL"
            }
            Text(command, color = when {
                command.startsWith("BUY") -> SnGreen
                command.startsWith("SELL") -> SnRed
                else -> SnGold
            }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (s != null) {
                RowLine("Entry acuan (open berjalan)", vm.runningOpen?.let { fmtUsd(it) } ?: "-")
                RowLine("Stop (Donchian low)", fmtUsd(s.donchianLow))
                RowLine("Harga berjalan acuan", vm.runningClose?.let { fmtUsd(it) } ?: "-")
                Spacer(Modifier.height(6.dp))
                Text("Risiko per trade paper:", color = SnTextDim, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5, 1.0, 2.0).forEach { pct ->
                        val sel = vm.paperRiskPct == pct
                        if (sel) {
                            Button(onClick = { vm.selectPaperRiskPct(pct) }, colors = ButtonDefaults.buttonColors(containerColor = SnGold, contentColor = Color(0xFF1A1200)), modifier = Modifier.weight(1f)) { Text(pct.toString() + "%", fontSize = 12.sp) }
                        } else {
                            OutlinedButton(onClick = { vm.selectPaperRiskPct(pct) }, modifier = Modifier.weight(1f)) { Text(pct.toString() + "%", color = SnText, fontSize = 12.sp) }
                        }
                    }
                }
                if (pos == null && action.startsWith("LONG")) {
                    val entryRef = vm.runningOpen ?: s.lastClose
                    val qty = paperQtyByRisk(vm.paperCash, entryRef, s.donchianLow, vm.paperRiskPct / 100.0)
                    val fill = paperBuyFill(entryRef)
                    val cost = paperBuyCost(qty, fill)
                    Spacer(Modifier.height(6.dp))
                    RowLine("Ukuran paper (risiko " + vm.paperRiskPct.toString() + "%)", String.format(Locale.US, "%.5f BTC", qty))
                    RowLine("Biaya paper (fill+fee)", fmtUsd(cost))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.paperBuy() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SnGreen, contentColor = Color(0xFF062A1A))
                ) { Text("BUY (paper)", fontSize = 13.sp) }
                Button(
                    onClick = { vm.paperSellManual() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SnRed)
                ) { Text("SELL (paper)", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.resetPaper() }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset paper ke $10.000", color = SnText, fontSize = 13.sp)
            }
            vm.paperNotice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = SnCyan, fontSize = 13.sp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Saldo & posisi PAPER", color = SnGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            RowLine("Cash paper", fmtUsd(vm.paperCash))
            val pos2 = vm.paperPosition
            if (pos2 != null) {
                RowLine("Posisi long paper", String.format(Locale.US, "%.5f BTC", pos2.qty))
                RowLine("Entry fill paper", fmtUsd(pos2.entryFillPrice))
                RowLine("Stop paper", fmtUsd(pos2.stopPrice))
                val unreal = vm.paperUnrealized()
                if (unreal != null) RowLine("Unrealized paper", fmtPct(unreal * 100), if (unreal >= 0) SnGreen else SnRed)
                Text("Posisi ini auto-exit di refresh berikutnya kalau close tembus Donchian low (exit di open berikutnya).", color = SnTextDim, fontSize = 12.sp)
            } else {
                RowLine("Posisi long paper", "TIDAK ADA")
            }
            Spacer(Modifier.height(10.dp))
            Text("Trade paper terakhir (maks 5)", color = SnGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (vm.paperTrades.isEmpty()) {
                Text("Belum ada trade paper.", color = SnTextDim, fontSize = 13.sp)
            } else {
                vm.paperTrades.takeLast(5).reversed().forEach { tr ->
                    RowLine(
                        fmtUsd(tr.entryFillPrice) + " -> " + fmtUsd(tr.exitFillPrice) + " (" + tr.reason + ")",
                        fmtPct(tr.returnFraction * 100),
                        if (tr.returnFraction >= 0) SnGreen else SnRed
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Semua order di kartu ini VIRTUAL dengan fee 0,1% + slippage 0,05% per sisi. Tidak ada API key, tidak ada order ke exchange. Testnet/real belum ada di build ini.", color = SnTextDim, fontSize = 12.sp)
        }
    }
}
