# LEICA FRAME — AI Professional Photography Editor

Aplikasi Android (Kotlin + Jetpack Compose + Material 3, MVVM) untuk menyunting foto dengan
warna gaya Leica, potret studio gaya Harcourt, rendering gaya Sony Xperia dan **AI Depth Portrait** — semuanya **offline di dalam telefon**.

> Style inspirations only. Not affiliated with, licensed or endorsed by Leica Camera AG, Sony Group or Studio Harcourt. No official logos or presets are used.

**Muat turun APK:** [Releases terkini](https://github.com/mohdrozaimi205/LeicaFrame/releases/latest) · **Laman web:** https://mohdrozaimi205.github.io/LeicaFrame/

## Ciri

| Modul | Apa yang sebenarnya berlaku |
|---|---|
| **AI Depth Portrait** | ML Kit Selfie Segmentation (rangkaian neural dalam peranti) → topeng subjek → penghalusan tepi 2-laluan *Guided Filter* (rambut, telinga, cermin mata, baju) → peta kedalaman (setiap subjek ada kedalaman sendiri, latar berlapis) → kabur kanta berubah ikut kedalaman dengan kernel cakera, penguat highlight (bola bokeh) dan pemberat latar (tiada halo). Bukan Gaussian blur seluruh latar. |
| Bukaan | f/1.0, 1.2, 1.4, 1.8, 2.0, 2.8, 4.0, 5.6, 8.0, 11 — nilai lebih kecil = kabur lebih kuat |
| Bokeh | Natural, Circular, Soft Cinematic (bujur anamorfik), Creamy, Lens Vintage (tepi *soap-bubble*) |
| Smart Focus | Ketik untuk fokus, slider jarak fokus & peralihan, kabur latar depan, kekalkan semua subjek tajam, pulihkan latar asal, berus tambah/padam subjek |
| Subjek bukan manusia | Sandaran anggaran saliency (kawasan tajam + keutamaan tengah) + berus manual |
| **Leica Collection** (12) | Lengkung tona, HSL, split-tone, bijirin, vignette — setiap profil parameter berbeza |
| **Harcourt Studio** (22) | Classic / Lighting / Tonal / Colour. Pencahayaan berarah (Rembrandt, Butterfly, Split, Side, Soft Key, Cinematic) diletakkan ikut muka yang dikesan + latar studio digelapkan guna topeng AI. 8 kawalan studio. |
| **Sony Xperia** (26) | Natural Colour, Creative Look (ST, PT, NT, VV, VV2, FL, FL2, FL3, IN, SH), Portrait, Professional Photography. Xperia 1 VII menyediakan ST/NT/VV/FL/IN/SH; VV2/FL2/FL3/PT berasal dari kamera Alpha. Parameter asli — bukan LUT Sony. |
| Cinematic / B&W / Film | 18 profil tambahan |
| AI Enhance | Colour correction, low-light, noise reduction (kroma kuat/luma ringan), face & skin enhancement (ML Kit face detection + guided filter, tekstur dikekalkan), detail, sharpness recovery |
| Watermark | 7 gaya bingkai/overlay. EXIF sebenar dibezakan daripada nilai simulasi (cth bukaan AI ditanda "sim") |
| Edit | Exposure, brightness, contrast, saturation, vibrance, temperature, tint, highlights, shadows, whites, blacks, sharpness, clarity, texture, fade, grain, vignette, glow, halation, HSL 8 jalur, tone curve 5 titik, sebelum/selepas (tekan & tahan) |
| Export | Original / 2K / 4K, JPEG sehingga 100% atau PNG, nisbah asal, EXIF kamera disalin. Gambar dibesarkan **ditanda sebagai interpolasi**, bukan butiran 4K sebenar. |

## Saiz & prestasi AI
- Model ML Kit (segmentasi + muka) dibundel dalam APK: tambah lebih kurang 5–10 MB. Tiada muat turun, tiada internet.
- Pratonton diproses pada ~1.6 MP (berbilang teras CPU). Eksport diproses pada resolusi penuh dengan had memori automatik (≈44 bait/piksel); gambar 50 MP mungkin dihadkan pada telefon RAM rendah dan aplikasi akan memberitahu.
- Pilihan masa depan: model super-resolution ONNX (Real-ESRGAN ×4, ~17–67 MB, 5–30 s sekeping) dan model kedalaman monokular (Depth-Anything-Small ONNX ~100 MB). Tidak dibundel sekarang untuk mengekalkan APK kecil.

## Bina
- Android Studio: *Open* folder ini → tunggu Gradle sync → **Run**.
- Automatik: setiap `push` ke `main` → GitHub Actions bina `assembleRelease` → APK di **Releases**.

## Seni bina
```
ui/ (Compose screens)  →  EditorViewModel (MVVM, StateFlow, debounce render)
                          ↓
image/Renderer: Enhance → DepthEngine → ColorEngine(Look) → StudioLighting → Spatial → Adjust → Watermark
ai/: Segmenter (ML Kit), DepthEngine, Enhance     export/: PhotoIO (MediaStore, EXIF)
```
Suntingan tidak merosakkan gambar asal (non-destructive): gambar asal tidak pernah diubah; semua tetapan adalah parameter.

Min SDK 26 (Android 8.0) · Target SDK 35 · Kemas kini automatik dalam app (v1.0.7+)
