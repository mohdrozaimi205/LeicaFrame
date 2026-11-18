package com.mohdrozaimi.leicaframe

import com.mohdrozaimi.leicaframe.ai.DepthEngine
import com.mohdrozaimi.leicaframe.ai.PortraitSettings
import com.mohdrozaimi.leicaframe.ai.BokehStyle
import com.mohdrozaimi.leicaframe.ai.Enhance
import com.mohdrozaimi.leicaframe.image.ColorEngine
import com.mohdrozaimi.leicaframe.image.FloatImage
import com.mohdrozaimi.leicaframe.image.LightStyle
import com.mohdrozaimi.leicaframe.image.Looks
import com.mohdrozaimi.leicaframe.image.StudioLighting
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ujian enjin imej pada imej sintetik (berjalan di GitHub Actions sebelum APK dibina).
 * Output PNG disimpan dalam app/build/test-out untuk semakan visual.
 */
class EngineTest {
    private val W = 480; private val H = 600
    private val out = File("build/test-out").apply { mkdirs() }

    /** Imej ujian: latar bertekstur + titik terang (untuk bokeh) + "orang" bujur di tengah. */
    private fun scene(): Pair<FloatImage, FloatArray> {
        val img = FloatImage(W, H)
        val mask = FloatArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            val i = y * W + x
            val checker = if (((x / 12) + (y / 12)) % 2 == 0) 0.25f else 0.55f
            img.r[i] = checker * 0.7f + 0.1f; img.g[i] = checker * 0.9f; img.b[i] = checker * 0.6f + 0.15f
            // titik lampu
            for ((cx, cy) in listOf(60 to 80, 400 to 120, 90 to 300, 420 to 420)) {
                val d = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toFloat())
                if (d < 6f) { img.r[i] = 1f; img.g[i] = 0.95f; img.b[i] = 0.8f }
            }
            // subjek: kepala + badan
            val hx = (x - 240f) / 70f; val hy = (y - 220f) / 85f
            val bx = (x - 240f) / 140f; val by = (y - 520f) / 190f
            if (hx * hx + hy * hy < 1f || (bx * bx + by * by < 1f && y > 330)) {
                img.r[i] = 0.85f; img.g[i] = 0.65f; img.b[i] = 0.52f
                if ((x + y) % 7 == 0) { img.r[i] = 0.6f; img.g[i] = 0.45f; img.b[i] = 0.35f } // tekstur
                mask[i] = 1f
            }
        }
        return img to mask
    }

    private fun save(img: FloatImage, name: String) {
        val bi = BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_RGB)
        bi.setRGB(0, 0, img.w, img.h, img.toArgb(), 0, img.w)
        ImageIO.write(bi, "png", File(out, "$name.png"))
    }

    private fun localVariance(img: FloatImage, x0: Int, y0: Int, size: Int): Float {
        var s = 0f; var s2 = 0f; var n = 0
        for (y in y0 until y0 + size) for (x in x0 until x0 + size) {
            val v = img.g[y * img.w + x]; s += v; s2 += v * v; n++
        }
        val m = s / n
        return s2 / n - m * m
    }

    private fun diff(a: FloatImage, b: FloatImage): Float {
        var d = 0.0
        for (i in a.r.indices) d += abs(a.r[i] - b.r[i]) + abs(a.g[i] - b.g[i]) + abs(a.b[i] - b.b[i])
        return (d / (a.r.size * 3)).toFloat()
    }

    @Test fun depthPortraitApertureControlsBlur() = runBlocking {
        val (img, mask) = scene()
        save(img, "00_original")
        val model = DepthEngine.build(img, mask, null, true)
        val results = HashMap<Float, Float>()
        for (f in listOf(1.0f, 1.4f, 2.8f, 5.6f, 11f)) {
            val o = img.copy()
            DepthEngine.render(o, model, PortraitSettings(enabled = true, aperture = f))
            save(o, "01_portrait_f$f")
            results[f] = localVariance(o, 20, 20, 60)      // latar (atas kiri)
            // subjek mesti kekal tajam
            val subj = localVariance(o, 220, 400, 40); val subj0 = localVariance(img, 220, 400, 40)
            assertTrue("Subjek mesti kekal tajam pada f/$f", subj > subj0 * 0.85f)
        }
        val v0 = localVariance(img, 20, 20, 60)
        println("Latar variance asal=$v0 hasil=$results")
        assertTrue(results[1.0f]!! < results[2.8f]!!)
        assertTrue(results[2.8f]!! < results[11f]!!)
        assertTrue(results[1.4f]!! < v0 * 0.5f)
        for (b in BokehStyle.entries) {
            val o = img.copy()
            DepthEngine.render(o, model, PortraitSettings(enabled = true, aperture = 1.4f, style = b))
            save(o, "02_bokeh_${b.name}")
        }
        // fokus pada latar: subjek pula kabur
        val o = img.copy()
        DepthEngine.render(o, model, PortraitSettings(enabled = true, aperture = 1.4f, focusDepth = 0.95f))
        save(o, "03_focus_background")
        assertTrue(localVariance(o, 220, 400, 40) < localVariance(img, 220, 400, 40) * 0.8f)
    }

    @Test fun everyLookIsDistinctAndVisible() = runBlocking {
        val (img, _) = scene()
        val outs = Looks.all.map { look ->
            val o = img.copy()
            ColorEngine.apply(o, look.tone, 1f)
            ColorEngine.applySpatial(o, look.tone, 1f)
            look to o
        }
        assertEquals("ID preset mesti unik", Looks.all.size, Looks.all.map { it.id }.toSet().size)
        for ((look, o) in outs) {
            val d = diff(img, o)
            assertTrue("${look.name} mesti mengubah imej (d=$d)", d > 0.005f)
        }
        for (i in outs.indices) for (j in i + 1 until outs.size) {
            val d = diff(outs[i].second, outs[j].second)
            assertTrue("${outs[i].first.name} vs ${outs[j].first.name} terlalu serupa (d=$d)", d > 0.0015f)
        }
        listOf("leica_standard", "leica_monochrom", "hc_rembrandt", "cl_fl", "cl_sh", "cl_vv", "fm_cine800t").forEach { id ->
            outs.first { it.first.id == id }.let { save(it.second, "10_look_$id") }
        }
        println("Preset diuji: ${outs.size}")
    }

    @Test fun harcourtLightingAndBackground() = runBlocking {
        val (img, mask) = scene()
        for (s in LightStyle.entries.filter { it != LightStyle.NONE }) {
            val o = img.copy()
            StudioLighting.apply(o, mask, emptyList(), StudioLighting.Params(s, 1f, 0.8f))
            save(o, "20_light_${s.name}")
            assertTrue("Latar mesti digelapkan", o.g[30 * W + 30] < img.g[30 * W + 30] * 0.6f)
            // muka kiri vs kanan berbeza untuk cahaya berarah
            if (s == LightStyle.SPLIT) assertTrue(o.g[220 * W + 200] > o.g[220 * W + 280] * 1.3f)
        }
    }

    @Test fun enhanceAndUpscale() = runBlocking {
        val (img, mask) = scene()
        val o = img.copy()
        Enhance.apply(o, com.mohdrozaimi.leicaframe.ai.EnhanceSettings(colorCorrect = true, lowLight = true, denoise = true,
            skinEnhance = true, detail = true, sharpRecover = true), emptyList(), mask)
        save(o, "30_enhance")
        assertTrue(diff(img, o) > 0.005f)
        val up = Enhance.upscale(img, 1536, 1920)
        assertEquals(1536, up.w); assertEquals(1920, up.h)
    }
}
