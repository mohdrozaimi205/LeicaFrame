package com.mohdrozaimi.leicaframe.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max

enum class WatermarkStyle(val label: String) {
    NONE("None"),
    WHITE_FRAME("Classic White Frame"),
    BLACK_FRAME("Noir Frame"),
    GALLERY("Gallery Border"),
    MINIMAL("Minimal Overlay"),
    SIGNATURE("Signature Centre"),
    FILM_DATE("Film Date Stamp"),
    RANGEFINDER("Rangefinder Plate"),
}

/** Satu nilai metadata + sumbernya (EXIF sebenar atau tetapan suntingan simulasi). */
data class MetaValue(val text: String, val simulated: Boolean)

data class WatermarkSettings(
    val style: WatermarkStyle = WatermarkStyle.NONE,
    val cameraName: String = "LEICA FRAME",
    val lens: String = "",
    val signature: String = "",
    val showCamera: Boolean = true,
    val showLens: Boolean = true,
    val showAperture: Boolean = true,
    val showShutter: Boolean = true,
    val showIso: Boolean = true,
    val showFocal: Boolean = true,
    val showDate: Boolean = true,
    val showSignature: Boolean = true,
    val markSimulated: Boolean = true,   // tanda "sim" pada nilai yang disimulasi
    val textScale: Float = 1f,
    val accent: Int = 0xFFC8102E.toInt(),
)

data class PhotoMeta(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val iso: String? = null,
    val focal: String? = null,
    val date: String? = null,
)

object Watermark {

    /** Gabung metadata EXIF sebenar + nilai simulasi (cth bukaan dari AI Portrait). */
    fun values(s: WatermarkSettings, meta: PhotoMeta, simulatedAperture: Float?): List<MetaValue> {
        val out = ArrayList<MetaValue>()
        if (s.showFocal) meta.focal?.let { out += MetaValue(it, false) }
        if (s.showAperture) {
            if (simulatedAperture != null) out += MetaValue("f/" + fmt(simulatedAperture), true)
            else meta.aperture?.let { out += MetaValue(it, false) }
        }
        if (s.showShutter) meta.shutter?.let { out += MetaValue(it, false) }
        if (s.showIso) meta.iso?.let { out += MetaValue(it, false) }
        return out
    }

    private fun fmt(f: Float) = if (f == f.toInt().toFloat()) "${f.toInt()}.0" else f.toString()

    private fun line(vals: List<MetaValue>, mark: Boolean) =
        vals.joinToString("   ") { if (it.simulated && mark) it.text + " sim" else it.text }

    /** Lukis tera air. Bingkai menambah ruang di luar imej (tidak memotong gambar). */
    fun draw(src: Bitmap, s: WatermarkSettings, meta: PhotoMeta, simulatedAperture: Float?): Bitmap {
        if (s.style == WatermarkStyle.NONE) return src
        val w = src.width; val h = src.height
        val unit = max(w, h) / 1000f * s.textScale
        val vals = values(s, meta, simulatedAperture)
        val camera = if (s.showCamera) s.cameraName.ifBlank { meta.model ?: "" } else ""
        val lens = if (s.showLens) s.lens.ifBlank { meta.lens ?: "" } else ""
        val date = if (s.showDate) meta.date ?: "" else ""
        val sig = if (s.showSignature) s.signature else ""
        val settingsLine = line(vals, s.markSimulated)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
        val reg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        val serif = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }

        return when (s.style) {
            WatermarkStyle.WHITE_FRAME, WatermarkStyle.BLACK_FRAME -> {
                val dark = s.style == WatermarkStyle.BLACK_FRAME
                val bar = (h * 0.085f).toInt().coerceAtLeast((60 * unit).toInt())
                val out = Bitmap.createBitmap(w, h + bar, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawColor(if (dark) Color.rgb(12, 12, 12) else Color.WHITE)
                c.drawBitmap(src, 0f, 0f, null)
                val fg = if (dark) Color.WHITE else Color.rgb(20, 20, 20)
                val sub = if (dark) Color.rgb(170, 170, 170) else Color.rgb(110, 110, 110)
                val pad = bar * 0.42f
                bold.color = fg; bold.textSize = bar * 0.28f
                reg.color = sub; reg.textSize = bar * 0.20f
                val cy = h + bar / 2f
                c.drawText(camera, pad, cy - bar * 0.02f, bold)
                c.drawText(lens, pad, cy + bar * 0.26f, reg)
                // kanan: tetapan & tarikh
                val rx = w - pad
                bold.textAlign = Paint.Align.RIGHT; reg.textAlign = Paint.Align.RIGHT
                c.drawText(settingsLine, rx, cy - bar * 0.02f, bold.apply { textSize = bar * 0.24f })
                c.drawText(listOf(date, sig).filter { it.isNotBlank() }.joinToString("  ·  "), rx, cy + bar * 0.26f, reg)
                // pemisah & tanda bulat asli (bukan logo jenama)
                val tw = max(bold.measureText(settingsLine), reg.measureText(date))
                val sx = rx - tw - pad * 0.6f
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sub; strokeWidth = max(1f, unit * 1.2f) }
                c.drawLine(sx, cy - bar * 0.25f, sx, cy + bar * 0.25f, line)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.accent; style = Paint.Style.STROKE; strokeWidth = bar * 0.05f }
                c.drawCircle(sx - pad * 0.9f, cy, bar * 0.16f, dot)
                out
            }
            WatermarkStyle.GALLERY -> {
                val side = (max(w, h) * 0.04f).toInt()
                val bottom = (max(w, h) * 0.12f).toInt()
                val out = Bitmap.createBitmap(w + side * 2, h + side + bottom, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawColor(Color.rgb(250, 249, 246))
                c.drawBitmap(src, side.toFloat(), side.toFloat(), null)
                val cx = out.width / 2f
                serif.color = Color.rgb(30, 30, 30); serif.textSize = bottom * 0.22f; serif.textAlign = Paint.Align.CENTER
                reg.color = Color.rgb(120, 120, 120); reg.textSize = bottom * 0.13f; reg.textAlign = Paint.Align.CENTER
                c.drawText(sig.ifBlank { camera }, cx, h + side + bottom * 0.45f, serif)
                c.drawText(listOf(if (sig.isNotBlank()) camera else "", lens, settingsLine, date).filter { it.isNotBlank() }
                    .joinToString("  |  "), cx, h + side + bottom * 0.72f, reg)
                out
            }
            else -> {
                val out = src.copy(Bitmap.Config.ARGB_8888, true)
                val c = Canvas(out)
                when (s.style) {
                    WatermarkStyle.MINIMAL -> {
                        val pad = 28 * unit
                        bold.color = Color.WHITE; bold.textSize = 26 * unit; bold.setShadowLayer(6 * unit, 0f, 0f, 0x88000000.toInt())
                        reg.color = Color.WHITE; reg.textSize = 18 * unit; reg.setShadowLayer(6 * unit, 0f, 0f, 0x88000000.toInt())
                        c.drawText(camera, pad, h - pad - 26 * unit, bold)
                        c.drawText(listOf(lens, settingsLine, date).filter { it.isNotBlank() }.joinToString("  ·  "), pad, h - pad, reg)
                    }
                    WatermarkStyle.SIGNATURE -> {
                        val cx = w / 2f
                        serif.color = Color.WHITE; serif.textSize = 46 * unit; serif.textAlign = Paint.Align.CENTER
                        serif.setShadowLayer(8 * unit, 0f, 0f, 0x99000000.toInt())
                        reg.color = Color.argb(230, 255, 255, 255); reg.textSize = 16 * unit; reg.textAlign = Paint.Align.CENTER
                        reg.letterSpacing = 0.25f; reg.setShadowLayer(6 * unit, 0f, 0f, 0x99000000.toInt())
                        c.drawText(sig.ifBlank { camera }, cx, h - 80 * unit, serif)
                        c.drawText(listOf(camera.uppercase(), settingsLine).filter { it.isNotBlank() }.joinToString("   "), cx, h - 44 * unit, reg)
                    }
                    WatermarkStyle.FILM_DATE -> {
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = Typeface.MONOSPACE; color = Color.rgb(255, 150, 40); textSize = 34 * unit
                            textAlign = Paint.Align.RIGHT; setShadowLayer(10 * unit, 0f, 0f, Color.rgb(255, 80, 0))
                        }
                        c.drawText(date.ifBlank { java.text.SimpleDateFormat("''yy MM dd", java.util.Locale.US).format(java.util.Date()) }, w - 40 * unit, h - 40 * unit, p)
                    }
                    WatermarkStyle.RANGEFINDER -> {
                        val ph = 64 * unit; val pw = max(260 * unit, bold.apply { textSize = 22 * unit }.measureText(camera) + 140 * unit)
                        val r = RectF(w - pw - 30 * unit, h - ph - 30 * unit, w - 30 * unit, h - 30 * unit)
                        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 15, 15, 15) }
                        c.drawRoundRect(r, 10 * unit, 10 * unit, bg)
                        bold.color = Color.WHITE
                        c.drawText(camera, r.left + 20 * unit, r.top + 28 * unit, bold)
                        reg.color = Color.rgb(200, 200, 200); reg.textSize = 15 * unit
                        c.drawText(listOf(settingsLine, date).filter { it.isNotBlank() }.joinToString("  "), r.left + 20 * unit, r.top + 50 * unit, reg)
                        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.accent }
                        c.drawCircle(r.right - 22 * unit, r.top + 22 * unit, 6 * unit, dot)
                    }
                    else -> {}
                }
                out
            }
        }
    }
}
