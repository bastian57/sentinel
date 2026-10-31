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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
private fun SentinelScreen(vm: AppViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        if (vm.signal == null) vm.refreshData()
    }
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)
    ) {
        Text("SENTINEL", color = SnGold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "BTC/USDT  •  4 jam  •  Spot only  •  PAPER ONLY",
            color = SnTextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "App ini TIDAK trading uang asli, TIDAK kirim order, dan TIDAK minta API key. Ini dashboard sinyal + backtest dari riset Fase 1-3.",
            color = SnCyan, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sinyal", "Backtest", "Tentang").forEachIndexed { idx, title ->
                val selected = vm.selectedTab == idx
                if (selected) {
                    Button(
                        onClick = { vm.selectTab(idx) },
                        colors = ButtonDefaults.buttonColors(containerColor = SnGold, contentColor = Color(0xFF1A1200)),
                        modifier = Modifier.weight(1f)
                    ) { Text(title, fontSize = 13.sp) }
                } else {
                    OutlinedButton(
                        onClick = { vm.selectTab(idx) },
                        modifier = Modifier.weight(1f)
                    ) { Text(title, color = SnText, fontSize = 13.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (vm.selectedTab == 0) {
            // Mode toggle
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
                        Text("Sinyal saat ini", color = SnTextDim, fontSize = 13.sp)
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
            } else if (!vm.isLoading) {
                Text("Belum ada data. Tekan Refresh sinyal.", color = SnTextDim, fontSize = 14.sp)
            }
        } else if (vm.selectedTab == 1) {
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
                    Text(
                        "Peringatan: angka full pemenang kena winner's curse karena dipilih dari banyak varian. Pakai angka out-of-sample sebagai ekspektasi jujur, bukan angka full.",
                        color = SnRed, fontSize = 13.sp
                    )
                }
            }
        } else {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SnSurface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tentang SENTINEL v1.0.0", color = SnGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("• Pasangan: BTC/USDT spot saja.", color = SnText, fontSize = 13.sp)
                    Text("• Timeframe: 4 jam.", color = SnText, fontSize = 13.sp)
                    Text("• Tanpa leverage, tanpa futures, tanpa short.", color = SnText, fontSize = 13.sp)
                    Text("• Tanpa API key dan tanpa order ke exchange.", color = SnText, fontSize = 13.sp)
                    Text("• Data: Binance Vision (publik) dengan cadangan Binance US.", color = SnText, fontSize = 13.sp)
                    Text("• Sinyal: Donchian20 + MA20>MA50, exit Donchian low. Mode Aman tambah ADX14 > 25 dan naik.", color = SnText, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Risiko: backtest bukan jaminan masa depan. Drawdown historis masih puluhan persen. Jangan pakai uang yang tidak siap hilang, dan jangan jadikan sinyal ini satu-satunya dasar keputusan.",
                        color = SnRed, fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("SENTINEL v1.0.0  •  by DAFTECH  •  Paper only", color = SnTextDim, fontSize = 12.sp)
    }
}
