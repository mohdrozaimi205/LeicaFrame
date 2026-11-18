package com.mohdrozaimi.leicaframe.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mohdrozaimi.leicaframe.EditorViewModel
import com.mohdrozaimi.leicaframe.Tab
import com.mohdrozaimi.leicaframe.ai.APERTURES
import com.mohdrozaimi.leicaframe.ai.BokehStyle
import com.mohdrozaimi.leicaframe.export.ExportFormat
import com.mohdrozaimi.leicaframe.export.ExportOptions
import com.mohdrozaimi.leicaframe.export.ExportSize
import com.mohdrozaimi.leicaframe.export.PhotoIO
import com.mohdrozaimi.leicaframe.image.Collection
import com.mohdrozaimi.leicaframe.image.Looks
import com.mohdrozaimi.leicaframe.image.ToneSet
import com.mohdrozaimi.leicaframe.watermark.Watermark
import com.mohdrozaimi.leicaframe.watermark.WatermarkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================ Rangka skrin editor
@Composable
private fun EditorLayout(
    vm: EditorViewModel,
    onTap: ((Float, Float) -> Unit)? = null,
    onDrag: ((Float, Float) -> Unit)? = null,
    overlay: Bitmap? = null,
    focus: Offset? = null,
    panel: @Composable () -> Unit,
) {
    val preview by vm.preview.collectAsState()
    val original by vm.original.collectAsState()
    val status by vm.status.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PhotoPreview(preview ?: original, original, Modifier.fillMaxWidth().weight(0.52f),
            overlay = overlay, focusPoint = focus, onTap = onTap, onDrag = onDrag,
            busy = status.rendering || status.depthBusy || status.analysing)
        Column(Modifier.fillMaxWidth().weight(0.48f).verticalScroll(rememberScrollState())) {
            Panel { panel(); Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NeedPhoto() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Label("Pilih gambar di HOME dahulu")
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ============================================================ HOME
@Composable
fun HomeScreen(vm: EditorViewModel, onCheckUpdate: () -> Unit, go: (Tab) -> Unit) {
    val ctxH = LocalContext.current
    val photo by vm.photo.collectAsState()
    val original by vm.original.collectAsState()
    val status by vm.status.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        if (photo == null) {
            Spacer(Modifier.height(40.dp))
            Text("Every photograph\ndeserves a frame.", color = LF.White, fontSize = 30.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text("Leica-inspired colour, Harcourt studio portraits, Xperia-inspired rendering and on-device AI depth — all offline.",
                color = LF.Mute, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(36.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1.4f).border(1.dp, LF.Line).clickable { pick() },
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).border(1.5.dp, LF.Amber, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center) { Text("+", color = LF.Amber, fontSize = 26.sp) }
                    Spacer(Modifier.height(14.dp))
                    Label("Choose photo", color = LF.White)
                }
            }
            if (status.loading) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator(color = LF.Amber) }
        } else {
            val p = photo!!
            original?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(260.dp).background(LF.Charcoal),
                    contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Label("Source")
                    Text("${p.width} × ${p.height}  ·  ${"%.1f".format(p.width * p.height / 1e6)} MP", color = LF.White, fontFamily = Mono, fontSize = 13.sp)
                    Text(listOfNotNull(p.meta.make, p.meta.model).joinToString(" ").ifBlank { "No camera EXIF" },
                        color = LF.Mute, fontSize = 12.sp)
                    Text(listOfNotNull(p.meta.focal, p.meta.aperture, p.meta.shutter, p.meta.iso).joinToString("  "),
                        color = LF.Mute, fontFamily = Mono, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label("AI analysis")
                    Text(when {
                        status.analysing -> "Analysing…"
                        status.personFound -> "Person · ${status.faces} face(s)"
                        else -> "Scene / object"
                    }, color = LF.Amber, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Chip("Change photo", false, { pick() })
            SectionTitle("Studio")
            val tiles = listOf(
                Triple(Tab.FILTERS, "Filters", "Leica · Harcourt · Xperia · Film"),
                Triple(Tab.PORTRAIT, "AI Portrait", "Depth, aperture f/1.0–f/11, bokeh"),
                Triple(Tab.EDIT, "Edit & Enhance", "Light, colour, HSL, curve, AI"),
                Triple(Tab.WATERMARK, "Watermark", "Camera-style frames & EXIF"),
            )
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (t, title, sub) ->
                        Column(Modifier.weight(1f).padding(vertical = 5.dp).border(1.dp, LF.Line).clickable { go(t) }.padding(14.dp)) {
                            Text(title, color = LF.White, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(sub, color = LF.Mute, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("v" + com.mohdrozaimi.leicaframe.update.UpdateChecker.currentVersion(ctxH), color = LF.Mute,
                fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Chip("Check for updates", false, onCheckUpdate)
        }
        Spacer(Modifier.height(12.dp))
        Text("Style inspirations only — not affiliated with or endorsed by Leica, Sony or Studio Harcourt.",
            color = LF.Mute.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

// ============================================================ FILTERS
@Composable
fun FiltersScreen(vm: EditorViewModel) {
    val photo by vm.photo.collectAsState()
    if (photo == null) { NeedPhoto(); return }
    val state by vm.state.collectAsState()
    val thumbs by vm.thumbs.collectAsState()
    var col by rememberSaveable { mutableIntStateOf(0) }
    val current = Collection.entries[col]
    val selected = Looks.byId(state.lookId)
    EditorLayout(vm) {
        ChipRow {
            Collection.entries.forEachIndexed { i, c -> Chip(c.title, i == col, { col = i }) }
        }
        val looks = vm.looksFor(current)
        looks.groupBy { it.group }.forEach { (group, items) ->
            Label(group, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { look ->
                    val sel = look.id == state.lookId
                    Column(Modifier.width(78.dp).clickable { vm.selectLook(if (sel) null else look.id) }) {
                        Box(Modifier.size(78.dp).border(if (sel) 2.dp else 0.dp, if (sel) LF.Amber else Color.Transparent)
                            .background(LF.Graphite)) {
                            thumbs[look.id]?.let {
                                Image(it.asImageBitmap(), look.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Text(look.name, color = if (sel) LF.Amber else LF.White, fontSize = 10.sp, maxLines = 2,
                            lineHeight = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (selected != null) {
            SectionTitle(selected.name)
            if (selected.note.isNotBlank()) Text(selected.note, color = LF.Mute, fontSize = 12.sp)
            LfSlider("Intensity", state.lookIntensity, { v -> vm.update { it.copy(lookIntensity = v) } }, 0f..1f)
            if (selected.collection == Collection.HARCOURT) {
                val h = state.harcourt
                fun set(b: (com.mohdrozaimi.leicaframe.image.HarcourtControls) -> com.mohdrozaimi.leicaframe.image.HarcourtControls) =
                    vm.update { it.copy(harcourt = b(it.harcourt)) }
                SectionTitle("Studio controls")
                LfSlider("Studio lighting", h.studioLight, { v -> set { it.copy(studioLight = v) } }, 0f..1.6f, { "${(it * 100).toInt()}%" })
                LfSlider("Shadow intensity", h.shadow, { v -> set { it.copy(shadow = v) } }, -1f..1f)
                LfSlider("Highlight intensity", h.highlight, { v -> set { it.copy(highlight = v) } })
                LfSlider("Contrast", h.contrast, { v -> set { it.copy(contrast = v) } })
                LfSlider("Skin tone (cool ↔ warm)", h.skinTone, { v -> set { it.copy(skinTone = v) } })
                LfSlider("Background darkness", h.backgroundDark, { v -> set { it.copy(backgroundDark = v) } }, 0f..1.5f, { "${(it * 100).toInt()}%" })
                LfSlider("Vignette", h.vignette, { v -> set { it.copy(vignette = v) } })
                LfSlider("Monochrome intensity", h.mono, { v -> set { it.copy(mono = v) } }, 0f..1f, { "${(it * 100).toInt()}%" })
                Text("Lighting is placed using on-device face detection; background darkening uses the AI subject mask.",
                    color = LF.Mute, fontSize = 11.sp)
            }
        } else {
            Text("Pilih satu filter. Tekan & tahan gambar untuk banding sebelum/selepas.", color = LF.Mute, fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// ============================================================ AI PORTRAIT
@Composable
fun PortraitScreen(vm: EditorViewModel) {
    val photo by vm.photo.collectAsState()
    if (photo == null) { NeedPhoto(); return }
    val state by vm.state.collectAsState()
    val status by vm.status.collectAsState()
    val depthMap by vm.depthMap.collectAsState()
    val p = state.portrait
    EditorLayout(
        vm,
        onTap = if (status.brush == 0) ({ x: Float, y: Float -> vm.tapFocus(x, y) }) else null,
        onDrag = if (status.brush != 0) ({ x: Float, y: Float -> vm.paint(x, y) }) else null,
        overlay = if (status.showDepthMap) depthMap else null,
        focus = if (p.focusX >= 0f && p.enabled) Offset(p.focusX, p.focusY) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).background(LF.Amber).clickable(enabled = !status.depthBusy) { vm.detectDepth() }
                .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (status.depthBusy) "DETECTING DEPTH…" else if (status.depthReady) "RE-DETECT DEPTH (AI)" else "AI DEPTH DETECTION",
                    color = LF.Black, fontSize = 12.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.width(10.dp))
            Chip(if (status.showDepthMap) "Hide map" else "Depth map", status.showDepthMap, { vm.toggleDepthMap() })
        }
        if (!status.depthReady) {
            Text("Tekan AI DEPTH DETECTION untuk mengasingkan subjek dan menjana peta kedalaman.", color = LF.Mute,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            return@EditorLayout
        }
        LfSwitch("Portrait effect", p.enabled, { v -> vm.setPortrait { it.copy(enabled = v) } },
            "Matikan untuk pulihkan latar asal")
        SectionTitle("Aperture  f/${p.aperture}")
        ChipRow {
            APERTURES.forEach { f -> Chip("f/$f", f == p.aperture, { vm.setPortrait { it.copy(aperture = f) } }) }
        }
        LfSlider("Background blur intensity", p.intensity, { v -> vm.setPortrait { it.copy(intensity = v) } }, 0f..1.5f,
            { "${(it * 100).toInt()}%" })
        LfSlider("Focus distance", if (p.focusDepth < 0f) 0.2f else p.focusDepth,
            { v -> vm.setPortrait { it.copy(focusDepth = v, focusX = -1f) } }, 0f..1f, { "%.2f".format(it) })
        LfSlider("Depth transition", p.transition, { v -> vm.setPortrait { it.copy(transition = v) } }, 0.03f..0.6f,
            { "%.2f".format(it) })
        Text("Ketik pada gambar untuk tukar titik fokus.", color = LF.Mute, fontSize = 11.sp)
        SectionTitle("Bokeh")
        ChipRow { BokehStyle.entries.forEach { b -> Chip(b.label, b == p.style, { vm.setPortrait { it.copy(style = b) } }) } }
        LfSwitch("Blur foreground objects", p.blurForeground, { v -> vm.setPortrait { it.copy(blurForeground = v) } })
        LfSwitch("Keep all subjects in focus", p.keepAllSubjects, { v -> vm.setPortrait { it.copy(keepAllSubjects = v) } },
            "Untuk gambar berbilang orang")
        SectionTitle("Manual subject correction")
        ChipRow {
            Chip("Off", status.brush == 0, { vm.setBrush(0) })
            Chip("+ Add subject", status.brush == 1, { vm.setBrush(1) })
            Chip("− Erase subject", status.brush == 2, { vm.setBrush(2) })
            Chip("Clear", false, { vm.clearBrush() })
        }
        if (status.brush != 0) LfSlider("Brush size", status.brushSize, { vm.setBrushSize(it) }, 0.01f..0.15f,
            { "${(it * 100).toInt()}" })
    }
}

// ============================================================ EDIT
private val editTabs = listOf("LIGHT", "COLOUR", "DETAIL", "EFFECTS", "HSL", "CURVE", "AI ENHANCE")
private val bandNames = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")
private val bandColors = listOf(0xFFE5484D, 0xFFF08A24, 0xFFF5D90A, 0xFF46A758, 0xFF12A594, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F)

@Composable
fun EditScreen(vm: EditorViewModel) {
    val photo by vm.photo.collectAsState()
    if (photo == null) { NeedPhoto(); return }
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var band by rememberSaveable { mutableIntStateOf(0) }
    val a = state.adjust
    fun set(b: (ToneSet) -> ToneSet) = vm.update { it.copy(adjust = b(it.adjust)) }
    EditorLayout(vm) {
        ChipRow { editTabs.forEachIndexed { i, t -> Chip(t, i == tab, { tab = i }) } }
        when (tab) {
            0 -> {
                LfSlider("Exposure", a.exposure, { v -> set { it.copy(exposure = v) } }, -2f..2f, { "%+.2f EV".format(it) })
                LfSlider("Brightness", a.brightness, { v -> set { it.copy(brightness = v) } })
                LfSlider("Contrast", a.contrast, { v -> set { it.copy(contrast = v) } })
                LfSlider("Highlights", a.highlights, { v -> set { it.copy(highlights = v) } })
                LfSlider("Shadows", a.shadows, { v -> set { it.copy(shadows = v) } })
                LfSlider("Whites", a.whites, { v -> set { it.copy(whites = v) } })
                LfSlider("Blacks", a.blacks, { v -> set { it.copy(blacks = v) } })
            }
            1 -> {
                LfSlider("Temperature", a.temperature, { v -> set { it.copy(temperature = v) } })
                LfSlider("Tint", a.tint, { v -> set { it.copy(tint = v) } })
                LfSlider("Saturation", a.saturation, { v -> set { it.copy(saturation = v) } })
                LfSlider("Vibrance", a.vibrance, { v -> set { it.copy(vibrance = v) } })
                LfSlider("Skin protection", a.skinProtect, { v -> set { it.copy(skinProtect = v) } }, 0f..1f)
            }
            2 -> {
                LfSlider("Sharpness", a.sharpness, { v -> set { it.copy(sharpness = v) } }, 0f..1f)
                LfSlider("Clarity", a.clarity, { v -> set { it.copy(clarity = v) } })
                LfSlider("Texture", a.texture, { v -> set { it.copy(texture = v) } })
            }
            3 -> {
                LfSlider("Fade", a.fade, { v -> set { it.copy(fade = v) } }, 0f..1f)
                LfSlider("Grain", a.grain, { v -> set { it.copy(grain = v) } }, 0f..1f)
                LfSlider("Grain size", a.grainSize, { v -> set { it.copy(grainSize = v) } }, 0.5f..3f, { "%.1f".format(it) })
                LfSlider("Vignette", a.vignette, { v -> set { it.copy(vignette = v) } })
                LfSlider("Glow", a.glow, { v -> set { it.copy(glow = v) } }, 0f..1f)
                LfSlider("Halation", a.halation, { v -> set { it.copy(halation = v) } }, 0f..1f)
            }
            4 -> {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    bandColors.forEachIndexed { i, c ->
                        Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                            .border(2.dp, if (i == band) LF.White else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                            .clickable { band = i })
                    }
                }
                Label(bandNames[band])
                fun setHsl(comp: Int, v: Float) = set { t -> t.copy(hsl = t.hsl.copyOf().also { it[band * 3 + comp] = v }) }
                LfSlider("Hue", a.hsl[band * 3], { setHsl(0, it) }, -30f..30f, { "%+.0f°".format(it) })
                LfSlider("Saturation", a.hsl[band * 3 + 1], { setHsl(1, it) })
                LfSlider("Luminance", a.hsl[band * 3 + 2], { setHsl(2, it) })
            }
            5 -> {
                Text("Tone curve (5-point master curve)", color = LF.Mute, fontSize = 12.sp)
                val names = listOf("Black point", "Shadows", "Midtones", "Highlights", "White point")
                names.forEachIndexed { i, n ->
                    LfSlider(n, a.curve[i], { v -> set { t -> t.copy(curve = t.curve.copyOf().also { it[i] = v }) } },
                        -0.15f..0.15f, { "%+.0f".format(it * 255) })
                }
                CurvePreview(a.curve)
            }
            else -> {
                val e = state.enhance
                fun se(b: (com.mohdrozaimi.leicaframe.ai.EnhanceSettings) -> com.mohdrozaimi.leicaframe.ai.EnhanceSettings) =
                    vm.update { it.copy(enhance = b(it.enhance)) }
                Text("Diproses dalam peranti. Muka & subjek dikesan dengan model ML Kit.", color = LF.Mute, fontSize = 11.sp)
                LfSwitch("AI Colour Correction", e.colorCorrect, { v -> se { it.copy(colorCorrect = v) } }, "Imbangan putih & paras automatik")
                LfSwitch("AI Low-Light Enhancement", e.lowLight, { v -> se { it.copy(lowLight = v) } })
                LfSwitch("AI Noise Reduction", e.denoise, { v -> se { it.copy(denoise = v) } })
                if (e.denoise) LfSlider("Noise reduction strength", e.denoiseStrength, { v -> se { it.copy(denoiseStrength = v) } }, 0f..1f)
                LfSwitch("AI Face Enhancement", e.faceEnhance, { v -> se { it.copy(faceEnhance = v) } }, "Licin kulit muka, mata lebih jelas")
                LfSwitch("AI Skin Enhancement", e.skinEnhance, { v -> se { it.copy(skinEnhance = v) } }, "Tekstur kulit asli dikekalkan")
                if (e.skinEnhance) LfSlider("Skin smoothing", e.skinStrength, { v -> se { it.copy(skinStrength = v) } }, 0f..1f)
                LfSwitch("AI Detail Enhancement", e.detail, { v -> se { it.copy(detail = v) } })
                LfSwitch("AI Sharpness Recovery", e.sharpRecover, { v -> se { it.copy(sharpRecover = v) } })
                Text("AI Image Upscaling: pilih 2K/4K semasa EXPORT. Ia interpolasi — bukan butiran sebenar.",
                    color = LF.Mute, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun CurvePreview(curve: FloatArray) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp).border(1.dp, LF.Line)) {
        val path = androidx.compose.ui.graphics.Path()
        for (k in 0..64) {
            val x = k / 64f
            val y = (x + com.mohdrozaimi.leicaframe.image.ColorEngine.curveAt(curve, x)).coerceIn(0f, 1f)
            val px = x * size.width; val py = (1f - y) * size.height
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawLine(LF.Line, Offset(0f, size.height), Offset(size.width, 0f))
        drawPath(path, LF.Amber, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

// ============================================================ WATERMARK
@Composable
fun WatermarkScreen(vm: EditorViewModel) {
    val photo by vm.photo.collectAsState()
    if (photo == null) { NeedPhoto(); return }
    val state by vm.state.collectAsState()
    val w = state.watermark
    fun set(b: (com.mohdrozaimi.leicaframe.watermark.WatermarkSettings) -> com.mohdrozaimi.leicaframe.watermark.WatermarkSettings) =
        vm.update { it.copy(watermark = b(it.watermark)) }
    EditorLayout(vm) {
        ChipRow { WatermarkStyle.entries.forEach { s -> Chip(s.label, s == w.style, { set { it.copy(style = s) } }) } }
        if (w.style == WatermarkStyle.NONE) {
            Text("Pilih gaya tera air.", color = LF.Mute, fontSize = 12.sp); return@EditorLayout
        }
        val tfColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LF.Amber, unfocusedBorderColor = LF.Line,
            focusedTextColor = LF.White, unfocusedTextColor = LF.White, focusedLabelColor = LF.Amber, unfocusedLabelColor = LF.Mute)
        OutlinedTextField(w.cameraName, { v -> set { it.copy(cameraName = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Camera-style signature") }, singleLine = true, colors = tfColors)
        OutlinedTextField(w.lens, { v -> set { it.copy(lens = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Lens information (kosong = EXIF)") }, singleLine = true, colors = tfColors)
        OutlinedTextField(w.signature, { v -> set { it.copy(signature = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Photography signature") }, singleLine = true, colors = tfColors)
        SectionTitle("Show")
        LfSwitch("Camera", w.showCamera, { v -> set { it.copy(showCamera = v) } })
        LfSwitch("Lens", w.showLens, { v -> set { it.copy(showLens = v) } })
        LfSwitch("Aperture", w.showAperture, { v -> set { it.copy(showAperture = v) } })
        LfSwitch("Shutter speed", w.showShutter, { v -> set { it.copy(showShutter = v) } })
        LfSwitch("ISO", w.showIso, { v -> set { it.copy(showIso = v) } })
        LfSwitch("Focal length", w.showFocal, { v -> set { it.copy(showFocal = v) } })
        LfSwitch("Date & time", w.showDate, { v -> set { it.copy(showDate = v) } })
        LfSwitch("Signature", w.showSignature, { v -> set { it.copy(showSignature = v) } })
        LfSwitch("Mark simulated values", w.markSimulated, { v -> set { it.copy(markSimulated = v) } },
            "Tanda \"sim\" pada nilai dari AI Portrait")
        LfSlider("Text size", w.textScale, { v -> set { it.copy(textScale = v) } }, 0.6f..1.8f, { "${(it * 100).toInt()}%" })
        SectionTitle("Data source")
        val p = photo!!
        val vals = Watermark.values(w.copy(showAperture = true, showFocal = true, showIso = true, showShutter = true), p.meta,
            if (state.portrait.enabled) state.portrait.aperture else null)
        if (vals.isEmpty()) Text("Tiada metadata EXIF dalam gambar ini.", color = LF.Mute, fontSize = 12.sp)
        vals.forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(v.text, color = LF.White, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Label(if (v.simulated) "Simulated edit" else "Camera EXIF", color = if (v.simulated) LF.Amber else LF.Mute)
            }
        }
    }
}

// ============================================================ MY PHOTOS
@Composable
fun PhotosScreen(vm: EditorViewModel, openEditor: () -> Unit) {
    val saved by vm.saved.collectAsState()
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf<PhotoIO.SavedPhoto?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshSaved() }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("${saved.size} exported · Pictures/LeicaFrame", modifier = Modifier.weight(1f))
            Chip("Refresh", false, { vm.refreshSaved() })
        }
        if (saved.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("Belum ada gambar dieksport") }
        } else LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(saved, key = { it.uri.toString() }) { s ->
                val thumb by produceState<Bitmap?>(null, s.uri) {
                    value = withContext(Dispatchers.IO) { PhotoIO.thumbnail(ctx, s.uri, 300) }
                }
                Box(Modifier.aspectRatio(1f).background(LF.Graphite).clickable { sel = s }) {
                    thumb?.let { Image(it.asImageBitmap(), s.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
    sel?.let { s ->
        Dialog(onDismissRequest = { sel = null }) {
            Column(Modifier.background(LF.Charcoal).padding(18.dp)) {
                Text(s.name, color = LF.White, fontSize = 14.sp)
                Text("${s.width} × ${s.height}", color = LF.Mute, fontFamily = Mono, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Edit again", false, { vm.load(s.uri); sel = null; openEditor() })
                    Chip("Share", false, {
                        val i = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, s.uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(i, "Share"))
                        sel = null
                    })
                    Chip("Close", false, { sel = null })
                }
            }
        }
    }
}

// ============================================================ EXPORT
@Composable
fun ExportDialog(vm: EditorViewModel, onDismiss: () -> Unit, onExport: (ExportOptions) -> Unit) {
    val photo by vm.photo.collectAsState()
    val p = photo ?: return
    var opt by remember { mutableStateOf(ExportOptions()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(LF.Charcoal).padding(20.dp)) {
            Text("Export", color = LF.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(4.dp))
            Text("Source ${p.width} × ${p.height}", color = LF.Mute, fontFamily = Mono, fontSize = 12.sp)
            SectionTitle("Resolution")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportSize.entries.forEach { s -> Chip(s.label, s == opt.size, { opt = opt.copy(size = s) }) }
            }
            val (ow, oh) = vm.outputSize(opt)
            Spacer(Modifier.height(8.dp))
            Text("Output $ow × $oh", color = LF.White, fontFamily = Mono, fontSize = 13.sp)
            val longSrc = maxOf(p.width, p.height)
            if (opt.size != ExportSize.ORIGINAL && opt.size.longEdge > longSrc) {
                Text("⚠ Sumber lebih kecil daripada ${opt.size.label}. Imej akan dibesarkan (interpolasi) — bukan butiran ${opt.size.label} sebenar.",
                    color = LF.Amber, fontSize = 11.sp)
            }
            SectionTitle("Format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f -> Chip(f.label, f == opt.format, { opt = opt.copy(format = f) }) }
            }
            if (opt.format == ExportFormat.JPEG) {
                LfSlider("JPEG quality", opt.quality.toFloat(), { opt = opt.copy(quality = it.toInt()) }, 60f..100f, { "${it.toInt()}%" })
                LfSwitch("Keep camera EXIF", opt.keepExif, { opt = opt.copy(keepExif = it) })
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().background(LF.Amber).clickable { onExport(opt) }.padding(14.dp),
                contentAlignment = Alignment.Center) {
                Text("SAVE TO GALLERY", color = LF.Black, letterSpacing = 2.sp, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = LF.Amber)
            Spacer(Modifier.height(14.dp))
            Label(text, color = LF.White)
        }
    }
}

@Composable
fun UpdateDialog(info: com.mohdrozaimi.leicaframe.update.UpdateInfo, current: String, onDownload: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        Column(Modifier.fillMaxWidth().background(LF.Charcoal).border(1.dp, LF.Amber).padding(22.dp)) {
            Label("Update available", color = LF.Amber)
            Spacer(Modifier.height(8.dp))
            Text("LEICA FRAME v${info.version}", color = LF.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Text("Versi anda: v$current  ·  ${info.sizeBytes / 1_000_000} MB", color = LF.Mute, fontFamily = Mono, fontSize = 12.sp)
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(info.notes, color = LF.Mute, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 6)
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().background(LF.Amber).clickable { onDownload() }.padding(14.dp),
                contentAlignment = Alignment.Center) {
                Text("MUAT TURUN & PASANG", color = LF.Black, letterSpacing = 2.sp, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clickable { onLater() }.padding(12.dp), contentAlignment = Alignment.Center) {
                Label("Nanti", color = LF.White)
            }
        }
    }
}
