package com.mohdrozaimi.leicaframe.ai

import android.graphics.RectF
import com.mohdrozaimi.leicaframe.image.ColorEngine
import com.mohdrozaimi.leicaframe.image.FloatImage
import com.mohdrozaimi.leicaframe.image.Pixels
import com.mohdrozaimi.leicaframe.image.clamp01
import com.mohdrozaimi.leicaframe.image.lerp
import com.mohdrozaimi.leicaframe.image.smoothstep
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class EnhanceSettings(
    val colorCorrect: Boolean = false,
    val lowLight: Boolean = false,
    val denoise: Boolean = false,
    val denoiseStrength: Float = 0.5f,
    val faceEnhance: Boolean = false,
    val skinEnhance: Boolean = false,
    val skinStrength: Float = 0.5f,
    val detail: Boolean = false,
    val sharpRecover: Boolean = false,
    val upscale: Boolean = false,      // digunakan semasa eksport
) {
    fun any() = colorCorrect || lowLight || denoise || faceEnhance || skinEnhance || detail || sharpRecover
}

/**
 * Modul peningkatan imej. Pengesanan muka/subjek menggunakan model ML Kit;
 * pemprosesan piksel menggunakan penapis memelihara tepi (guided filter) supaya
 * tekstur kulit asli kekal dan tiada penajaman berlebihan.
 */
object Enhance {

    suspend fun apply(img: FloatImage, s: EnhanceSettings, faces: List<RectF>, subjectMask: FloatArray?) {
        if (!s.any()) return
        val w = img.w; val h = img.h
        val scale = max(w, h) / 1000f
        if (s.colorCorrect) autoColor(img)
        if (s.lowLight) lowLight(img)
        if (s.denoise || s.lowLight) denoise(img, if (s.denoise) s.denoiseStrength else 0.3f, scale)
        if (s.skinEnhance || s.faceEnhance) skin(img, s, faces, subjectMask, scale)
        if (s.detail) detail(img, scale)
        if (s.sharpRecover) sharpen(img, scale)
    }

    /** Imbangan putih "grey-world" pada ton tengah + paras automatik (0.4% / 99.6%). */
    private fun autoColor(img: FloatImage) {
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
        val hist = IntArray(256)
        for (i in img.r.indices step 3) {
            val l = 0.2126f * img.r[i] + 0.7152f * img.g[i] + 0.0722f * img.b[i]
            hist[(l * 255).toInt().coerceIn(0, 255)]++
            if (l in 0.15f..0.85f) { sr += img.r[i]; sg += img.g[i]; sb += img.b[i]; n++ }
        }
        if (n < 100) return
        val avg = (sr + sg + sb) / 3.0
        // pembetulan separa (60%) supaya suasana asal tidak hilang
        val kr = lerp(1f, (avg / sr).toFloat(), 0.6f)
        val kg = lerp(1f, (avg / sg).toFloat(), 0.6f)
        val kb = lerp(1f, (avg / sb).toFloat(), 0.6f)
        val total = hist.sum()
        var acc = 0; var lo = 0; var hi = 255
        for (k in 0..255) { acc += hist[k]; if (acc > total * 0.004f) { lo = k; break } }
        acc = 0
        for (k in 255 downTo 0) { acc += hist[k]; if (acc > total * 0.004f) { hi = k; break } }
        val blo = min(lo, 40) / 255f
        val bhi = max(hi, 200) / 255f
        val inv = 1f / max(0.2f, bhi - blo)
        for (i in img.r.indices) {
            img.r[i] = clamp01((img.r[i] * kr - blo) * inv)
            img.g[i] = clamp01((img.g[i] * kg - blo) * inv)
            img.b[i] = clamp01((img.b[i] * kb - blo) * inv)
        }
    }

    /** Pencahayaan rendah: gamma adaptif berdasarkan purata kecerahan + angkat bayang. */
    private fun lowLight(img: FloatImage) {
        var mean = 0.0
        for (i in img.r.indices step 4) mean += 0.2126f * img.r[i] + 0.7152f * img.g[i] + 0.0722f * img.b[i]
        mean /= (img.r.size / 4.0)
        val m = mean.toFloat().coerceIn(0.02f, 0.9f)
        val gamma = if (m < 0.42f) (ln(0.42f) / ln(m)).coerceIn(0.45f, 1f) else 1f
        for (i in img.r.indices) {
            val l = max(1e-4f, 0.2126f * img.r[i] + 0.7152f * img.g[i] + 0.0722f * img.b[i])
            var nl = l.pow(gamma)
            nl += 0.12f * (1f - smoothstep(0f, 0.4f, nl)) * nl
            val k = min(6f, nl / l)
            img.r[i] = clamp01(img.r[i] * k); img.g[i] = clamp01(img.g[i] * k); img.b[i] = clamp01(img.b[i] * k)
        }
    }

    /** Nyah-hingar: kroma ditapis kuat, luma ditapis ringan (memelihara tepi). */
    private fun denoise(img: FloatImage, strength: Float, scale: Float) {
        val w = img.w; val h = img.h; val n = w * h
        val y = FloatArray(n); val cb = FloatArray(n); val cr = FloatArray(n)
        for (i in 0 until n) {
            val yy = 0.299f * img.r[i] + 0.587f * img.g[i] + 0.114f * img.b[i]
            y[i] = yy; cb[i] = img.b[i] - yy; cr[i] = img.r[i] - yy
        }
        val rc = max(2, (4f * scale).toInt())
        val cb2 = Pixels.guidedFilter(y, cb, w, h, rc, 0.01f)
        val cr2 = Pixels.guidedFilter(y, cr, w, h, rc, 0.01f)
        val ry = max(1, (1.5f * scale).toInt())
        val eps = (0.025f * strength + 0.004f).let { it * it }
        val y2 = Pixels.guidedFilter(y, y, w, h, ry, eps)
        val keep = 1f - 0.75f * strength
        for (i in 0 until n) {
            val ny = lerp(y2[i], y[i], keep * 0.6f)
            val nb = lerp(cb2[i], cb[i], keep * 0.3f)
            val nr = lerp(cr2[i], cr[i], keep * 0.3f)
            val r = nr + ny; val b = nb + ny
            val g = (ny - 0.299f * r - 0.114f * b) / 0.587f
            img.r[i] = clamp01(r); img.g[i] = clamp01(g); img.b[i] = clamp01(b)
        }
    }

    /** Kulit & muka: licin lembut pada kawasan kulit sahaja, kekalkan ~35% tekstur halus. */
    private fun skin(img: FloatImage, s: EnhanceSettings, faces: List<RectF>, mask: FloatArray?, scale: Float) {
        val w = img.w; val h = img.h; val n = w * h
        val lum = img.luminance()
        val r = max(2, (5f * scale).toInt())
        val sr = Pixels.guidedFilter(lum, img.r, w, h, r, 0.0025f)
        val sg = Pixels.guidedFilter(lum, img.g, w, h, r, 0.0025f)
        val sb = Pixels.guidedFilter(lum, img.b, w, h, r, 0.0025f)
        val faceW = FloatArray(n)
        if (s.faceEnhance) {
            for (f in faces) {
                val cx = f.centerX() * w; val cy = f.centerY() * h
                val rx = f.width() * w * 0.62f; val ry = f.height() * h * 0.70f
                val x0 = max(0, (cx - rx).toInt()); val x1 = min(w - 1, (cx + rx).toInt())
                val y0 = max(0, (cy - ry).toInt()); val y1 = min(h - 1, (cy + ry).toInt())
                for (yy in y0..y1) for (xx in x0..x1) {
                    val dx = (xx - cx) / rx; val dy = (yy - cy) / ry
                    val d = sqrt(dx * dx + dy * dy)
                    val v = 1f - smoothstep(0.7f, 1f, d)
                    val i = yy * w + xx
                    if (v > faceW[i]) faceW[i] = v
                }
            }
        }
        val amount = if (s.skinEnhance) s.skinStrength else 0.35f
        for (i in 0 until n) {
            val sk = ColorEngine.skinWeight(img.r[i], img.g[i], img.b[i])
            var wgt = if (s.skinEnhance) sk * (mask?.get(i)?.let { 0.4f + 0.6f * it } ?: 1f) else 0f
            if (s.faceEnhance) wgt = max(wgt, sk * faceW[i])
            if (wgt <= 0f) continue
            val a = clamp01(wgt * amount * 0.65f / 0.5f).coerceAtMost(0.65f) // maks 65% licin
            img.r[i] = lerp(img.r[i], sr[i], a)
            img.g[i] = lerp(img.g[i], sg[i], a)
            img.b[i] = lerp(img.b[i], sb[i], a)
            if (s.faceEnhance && faceW[i] > 0f) {
                // sedikit cahaya pada muka
                val k = 1f + 0.04f * faceW[i]
                img.r[i] = clamp01(img.r[i] * k); img.g[i] = clamp01(img.g[i] * k); img.b[i] = clamp01(img.b[i] * k)
            }
        }
        if (s.faceEnhance) {
            // kejelasan mata/kening: bahagian atas muka
            val lum2 = img.luminance()
            val bl = Pixels.gaussian(lum2, w, h, max(1f, 2f * scale))
            for (f in faces) {
                val x0 = max(0, (f.left * w).toInt()); val x1 = min(w - 1, (f.right * w).toInt())
                val y0 = max(0, ((f.top + f.height() * 0.2f) * h).toInt())
                val y1 = min(h - 1, ((f.top + f.height() * 0.55f) * h).toInt())
                for (yy in y0..y1) for (xx in x0..x1) {
                    val i = yy * w + xx
                    val d = ((lum2[i] - bl[i]) * 0.9f).coerceIn(-0.06f, 0.06f)
                    img.r[i] = clamp01(img.r[i] + d); img.g[i] = clamp01(img.g[i] + d); img.b[i] = clamp01(img.b[i] + d)
                }
            }
        }
    }

    /** Peningkatan perincian berbilang skala (berasaskan guided filter — tiada halo). */
    private fun detail(img: FloatImage, scale: Float) {
        val w = img.w; val h = img.h
        val lum = img.luminance()
        val base1 = Pixels.guidedFilter(lum, lum, w, h, max(1, (2f * scale).toInt()), 0.0004f)
        val base2 = Pixels.guidedFilter(lum, lum, w, h, max(2, (8f * scale).toInt()), 0.002f)
        for (i in lum.indices) {
            val d = ((lum[i] - base1[i]) * 0.6f + (base1[i] - base2[i]) * 0.35f).coerceIn(-0.08f, 0.08f)
            img.r[i] = clamp01(img.r[i] + d); img.g[i] = clamp01(img.g[i] + d); img.b[i] = clamp01(img.b[i] + d)
        }
    }

    /** Pemulihan ketajaman: unsharp mask dengan ambang hingar & had halo. */
    private fun sharpen(img: FloatImage, scale: Float) {
        val w = img.w; val h = img.h
        val lum = img.luminance()
        val bl = Pixels.gaussian(lum, w, h, max(0.7f, 1.0f * scale))
        for (i in lum.indices) {
            var d = lum[i] - bl[i]
            if (kotlin.math.abs(d) < 0.008f) d = 0f
            d = (d * 1.1f).coerceIn(-0.05f, 0.05f)
            img.r[i] = clamp01(img.r[i] + d); img.g[i] = clamp01(img.g[i] + d); img.b[i] = clamp01(img.b[i] + d)
        }
    }

    /**
     * Penskalaan naik (interpolasi berperingkat 1.5x + pemulihan tepi).
     * NOTA: ini interpolasi — ia TIDAK mencipta butiran 4K sebenar.
     */
    fun upscale(img: FloatImage, dw: Int, dh: Int): FloatImage {
        var cur = img
        while (cur.w * 1.5f < dw && cur.h * 1.5f < dh) {
            cur = Pixels.resizeImage(cur, (cur.w * 1.5f).toInt(), (cur.h * 1.5f).toInt())
            restoreEdges(cur)
        }
        cur = Pixels.resizeImage(cur, dw, dh)
        restoreEdges(cur)
        return cur
    }

    private fun restoreEdges(img: FloatImage) {
        val lum = img.luminance()
        val base = Pixels.guidedFilter(lum, lum, img.w, img.h, 2, 0.0006f)
        for (i in lum.indices) {
            val d = ((lum[i] - base[i]) * 0.45f).coerceIn(-0.04f, 0.04f)
            img.r[i] = clamp01(img.r[i] + d); img.g[i] = clamp01(img.g[i] + d); img.b[i] = clamp01(img.b[i] + d)
        }
    }
}
