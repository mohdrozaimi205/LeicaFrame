package com.mohdrozaimi.leicaframe.image

import android.graphics.Bitmap
import android.graphics.RectF
import com.mohdrozaimi.leicaframe.ai.DepthEngine
import com.mohdrozaimi.leicaframe.ai.DepthModel
import com.mohdrozaimi.leicaframe.ai.Enhance
import com.mohdrozaimi.leicaframe.ai.EnhanceSettings
import com.mohdrozaimi.leicaframe.ai.PortraitSettings
import com.mohdrozaimi.leicaframe.watermark.PhotoMeta
import com.mohdrozaimi.leicaframe.watermark.Watermark
import com.mohdrozaimi.leicaframe.watermark.WatermarkSettings

/** Kawalan tambahan koleksi Harcourt (semua 0 = ikut preset). */
data class HarcourtControls(
    val studioLight: Float = 1f,      // 0..1.6 pengganda
    val shadow: Float = 0f,           // -1..1
    val highlight: Float = 0f,        // -1..1
    val contrast: Float = 0f,         // -1..1
    val skinTone: Float = 0f,         // -1..1 (sejuk..hangat)
    val backgroundDark: Float = 1f,   // 0..1.5 pengganda
    val vignette: Float = 0f,         // -1..1
    val mono: Float = 1f,             // 0..1 pengganda monokrom
)

/** Keadaan suntingan lengkap — tidak merosakkan gambar asal (non-destructive). */
data class EditState(
    val lookId: String? = null,
    val lookIntensity: Float = 1f,
    val harcourt: HarcourtControls = HarcourtControls(),
    val adjust: ToneSet = ToneSet.IDENTITY,
    val enhance: EnhanceSettings = EnhanceSettings(),
    val portrait: PortraitSettings = PortraitSettings(),
    val watermark: WatermarkSettings = WatermarkSettings(),
)

/** Hasil analisis AI untuk satu imej. */
class Analysis(val w: Int, val h: Int, val mask: FloatArray, val faces: List<RectF>, val personFound: Boolean)

object Renderer {

    fun effectiveLook(state: EditState): Pair<ToneSet, StudioLighting.Params>? {
        val look = Looks.byId(state.lookId) ?: return null
        var t = look.tone
        var light = StudioLighting.Params(look.light, look.lightStrength, look.backgroundDark)
        if (look.collection == Collection.HARCOURT) {
            val c = state.harcourt
            t = t.copy(
                contrast = t.contrast + c.contrast * 0.45f,
                shadows = t.shadows - c.shadow * 0.6f,
                highlights = t.highlights + c.highlight * 0.6f,
                skinWarmth = t.skinWarmth + c.skinTone * 0.8f,
                temperature = if (t.mono < 1f) t.temperature + c.skinTone * 0.08f else t.temperature,
                vignette = (t.vignette + c.vignette * 0.6f).coerceIn(-1f, 1f),
                mono = (t.mono * c.mono).coerceIn(0f, 1f),
            )
            light = light.copy(strength = look.lightStrength * c.studioLight,
                backgroundDark = (look.backgroundDark * c.backgroundDark).coerceIn(0f, 1f))
        }
        return t to light
    }

    /**
     * Saluran penuh: AI Enhance → Potret Kedalaman → Preset (warna + pencahayaan studio + kesan)
     * → Pelarasan manual → Tera air.
     */
    suspend fun render(
        src: FloatImage,
        analysis: Analysis?,
        depth: DepthModel?,
        state: EditState,
        meta: PhotoMeta,
        withWatermark: Boolean = true,
    ): Bitmap {
        val img = src.copy()
        val mask: FloatArray? = when {
            depth != null && depth.w == img.w -> depth.mask
            analysis != null && analysis.w == img.w && analysis.h == img.h -> analysis.mask
            analysis != null -> Pixels.resize(analysis.mask, analysis.w, analysis.h, img.w, img.h)
            else -> null
        }
        val faces = analysis?.faces ?: emptyList()

        Enhance.apply(img, state.enhance, faces, mask)
        if (depth != null && state.portrait.enabled) DepthEngine.render(img, depth, state.portrait)

        effectiveLook(state)?.let { (tone, light) ->
            val k = state.lookIntensity
            ColorEngine.apply(img, tone, k)
            if (light.style != LightStyle.NONE || light.backgroundDark > 0f) {
                StudioLighting.apply(img, mask?.takeIf { analysis?.personFound == true || depth != null },
                    faces, light.copy(strength = light.strength * k, backgroundDark = light.backgroundDark * k))
            }
            ColorEngine.applySpatial(img, tone, k)
        }
        ColorEngine.apply(img, state.adjust, 1f)
        ColorEngine.applySpatial(img, state.adjust, 1f)

        var bmp = Bitmap.createBitmap(img.toArgb(), img.w, img.h, Bitmap.Config.ARGB_8888)
        if (withWatermark) {
            val sim = if (state.portrait.enabled) state.portrait.aperture else null
            bmp = Watermark.draw(bmp, state.watermark, meta, sim)
        }
        return bmp
    }

    fun toFloat(bmp: Bitmap): FloatImage {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return FloatImage.fromArgb(px, bmp.width, bmp.height)
    }
}
