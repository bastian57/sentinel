# SENTINEL v2.0.0 — Simulator Disiplin (VIRTUAL ONLY)

Paket: com.daftech.sentinel, versionCode 2, versionName 2.0.0.
Update dari v1.0.0 dengan keystore tetap yang sama, jadi install sebagai update
(tanpa uninstall) selama sertifikat APK cocok dengan kunci tetap Putra.

## Yang berubah dari v1
- Tab baru: Virtual (Ledger Robot vs Ledger User, counterfactual 3 kurva utuh,
  breaker virtual, panic exit virtual) dan Disiplin (follow rate, panic count,
  missed winner / avoided loser, kalkulator panic).
- Sinyal tetap v1: Donchian20 + MA20>MA50, exit Donchian low, Mode Aman tambah
  ADX14 > 25 dan naik, sinyal dari candle tutup saja.
- TIDAK ADA filter baru: V3 (skip ATR) dan Kronos TIDAK dipakai — keduanya
  gugur di pengujian otonom 2 Nov 2026.

## Batas keras
- VIRTUAL ONLY, paper only. Tanpa uang asli, tanpa order nyata, tanpa API key,
  tanpa leverage/futures/short, tanpa tombol beli/jual nyata.
- Keputusan user (Ikut/Lewati/Panic) hanya untuk sinyal terakhir yang sudah
  tutup. Sinyal lama yang tidak diputuskan dicatat Terlewat dan TIDAK bisa
  diambil retroaktif.
- HALT: gap data >1 candle, timestamp duplikat, atau warmup <50 candle ->
  banner HALT, tidak ada sinyal baru sampai data lengkap.
- Circuit breaker virtual: warning DD -25%, pause DD -30% (resume manual).
- Cooldown: setelah trade loss virtual ditutup, entry 3 candle berikutnya di-skip.

## Gate sebelum release (di CI)
- `gradle testDebugUnitTest` (VirtualEngineTest) harus lolos SEBELUM assemble.
- Gerbang keystore + sertifikat APK di workflow harus lolos, sama seperti v1.
- File workflow v2: workflow-sentinel-v2.yml di root zip ini, disalin ke
  .github/workflows/sentinel.yml saat push.
