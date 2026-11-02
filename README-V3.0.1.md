# SENTINEL v3.0.1 — Perintah + Paper Trading (PAPER ONLY)

Paket: com.daftech.sentinel, versionCode 4, versionName 3.0.1.
Update dari v2.0.0 dengan keystore tetap yang sama, jadi install sebagai update
(tanpa uninstall) selama sertifikat APK cocok dengan kunci tetap Putra.

## Yang berubah dari v2
- Tab Sinyal jadi meja perintah: BUY / SELL-exit / TUNGGU dalam bahasa jelas,
  entry acuan (open candle berjalan), stop Donchian low, harga berjalan acuan,
  dan kalkulator ukuran posisi dari risiko per trade (0,5% / 1% / 2%).
- Paper trading di dalam app: tombol BUY (paper) hanya saat sinyal LONG dan
  belum ada posisi; tombol SELL (paper) menutup posisi long paper yang dipegang.
  SELL di v3 HANYA berarti jual posisi long — TIDAK ADA short.
  Posisi paper auto-exit di refresh berikutnya kalau close tembus Donchian low
  (exit di open berikutnya, sesuai aturan G).
- Fill paper meniru market order: fee 0,1% per sisi + slippage merugikan 0,05%
  per sisi. Saldo paper awal $10.000, 1 posisi, tersimpan di HP (SharedPreferences).
- Perbaikan UI dari screenshot v2: bar tab diubah jadi horizontal-scroll dengan
  label satu baris, supaya tidak lagi terpotong ("Sin yal" dst).

## Batas keras (Tahap 1 dari rencana v3)
- PAPER ONLY. Build ini BELUM testnet dan BELUM real: tidak ada API key,
  tidak ada order ke exchange, tidak ada uang asli.
- LONG SPOT ONLY. Tanpa short, tanpa futures, tanpa leverage. Riset short
  cermin G GUGUR 2 Nov 2026 (out -40,9%, PF 0,74) dan tidak dipakai di v3.
- Sinyal tetap G/Aman v1/v2: Donchian20 + MA20>MA50, exit Donchian low,
  Mode Aman tambah ADX14 > 25 dan naik, sinyal dari candle tutup saja.
- Tahap berikutnya (BUKAN di build ini): testnet spot (jalur API uang mainan),
  lalu real spot — real butuh GAS TERPISAH lagi dari Putra, modal uji yang
  ia tentukan sendiri, kunci API input di app tanpa izin withdrawal, dan
  breaker aktif. Jangan klaim testnet/real sudah ada sebelum tahap itu digas.

## Gate sebelum release (di CI)
- `gradle testDebugUnitTest` (VirtualEngineTest + PaperEngineTest) harus lolos
  SEBELUM assemble.
- Gerbang keystore + sertifikat APK di workflow harus lolos, sama seperti v1/v2.
- File workflow v3: workflow-sentinel-v3.yml di root zip ini, disalin ke
  .github/workflows/sentinel.yml saat push.

## Perubahan v3.0.1 (hanya UI tab)
- Bar tab diganti dari Button Material3 menjadi chip klik custom (padding
  horizontal 16dp, tanpa lebar minimum Button). Akar bug v3.0.0: Button di Row
  scroll masih memakai weight(1f) + padding/lebar minimum Material3, sehingga
  teks terpotong jadi "Siny/Virt/Disi/Bac/Ten".
- Tidak ada perubahan mesin: sinyal, PaperEngine, dan paper trading sama
  persis dengan v3.0.0.
