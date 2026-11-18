package com.mohdrozaimi.leicaframe

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdrozaimi.leicaframe.ai.DepthEngine
import com.mohdrozaimi.leicaframe.ai.DepthModel
import com.mohdrozaimi.leicaframe.ai.Enhance
import com.mohdrozaimi.leicaframe.ai.PortraitSettings
import com.mohdrozaimi.leicaframe.ai.Segmenter
import com.mohdrozaimi.leicaframe.export.ExportOptions
import com.mohdrozaimi.leicaframe.export.ExportSize
import com.mohdrozaimi.leicaframe.export.PhotoIO
import com.mohdrozaimi.leicaframe.image.Analysis
import com.mohdrozaimi.leicaframe.image.Collection
import com.mohdrozaimi.leicaframe.image.EditState
import com.mohdrozaimi.leicaframe.image.FloatImage
import com.mohdrozaimi.leicaframe.image.Looks
import com.mohdrozaimi.leicaframe.image.Pixels
import com.mohdrozaimi.leicaframe.image.Renderer
import com.mohdrozaimi.leicaframe.image.clamp01
import com.mohdrozaimi.leicaframe.watermark.PhotoMeta
import com.mohdrozaimi.leicaframe.watermark.Watermark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PhotoInfo(
    val uri: Uri,
    val width: Int, val height: Int,
    val meta: PhotoMeta,
    val exif: ExifInterface?,
)

data class UiStatus(
    val loading: Boolean = false,
    val analysing: Boolean = false,
    val depthBusy: Boolean = false,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
    val personFound: Boolean = false,
    val faces: Int = 0,
    val depthReady: Boolean = false,
    val showDepthMap: Boolean = false,
    val brush: Int = 0,          // 0 tiada, 1 tambah subjek, 2 padam subjek
    val brushSize: Float = 0.05f,
    val message: String? = null,
)

@OptIn(FlowPreview::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    private val _photo = MutableStateFlow<PhotoInfo?>(null)
    val photo: StateFlow<PhotoInfo?> = _photo.asStateFlow()

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    private val _original = MutableStateFlow<Bitmap?>(null)
    val original: StateFlow<Bitmap?> = _original.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _depthMap = MutableStateFlow<Bitmap?>(null)
    val depthMap: StateFlow<Bitmap?> = _depthMap.asStateFlow()

    private val _status = MutableStateFlow(UiStatus())
    val status: StateFlow<UiStatus> = _status.asStateFlow()

    private val _thumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<String, Bitmap>> = _thumbs.asStateFlow()

    private val _saved = MutableStateFlow<List<PhotoIO.SavedPhoto>>(emptyList())
    val saved: StateFlow<List<PhotoIO.SavedPhoto>> = _saved.asStateFlow()

    private var previewF: FloatImage? = null
    private var analysis: Analysis? = null
    private var depth: DepthModel? = null
    private var edits: FloatArray? = null
    private var thumbJob: Job? = null
    private val depthVersion = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(_state, depthVersion) { s, _ -> s }.debounce(40).collectLatest { s ->
                val src = previewF ?: return@collectLatest
                _status.update { it.copy(rendering = true) }
                val bmp = withContext(Dispatchers.Default) {
                    Renderer.render(src, analysis, depth, s, _photo.value?.meta ?: PhotoMeta())
                }
                _preview.value = bmp
                _status.update { it.copy(rendering = false) }
            }
        }
        refreshSaved()
    }

    fun message(m: String?) = _status.update { it.copy(message = m) }

    // ------------------------------------------------------------------ muat gambar
    fun load(uri: Uri) {
        viewModelScope.launch {
            _status.value = UiStatus(loading = true)
            try {
                val (info, bmp) = withContext(Dispatchers.IO) {
                    val i = PhotoIO.readInfo(ctx, uri)
                    val b = PhotoIO.decode(ctx, uri, 1_600_000L)
                    i to b
                }
                _photo.value = PhotoInfo(uri, info.width, info.height, info.meta, info.exif)
                _original.value = bmp
                previewF = Renderer.toFloat(bmp)
                depth = null; edits = null; _depthMap.value = null
                _state.update { it.copy(portrait = it.portrait.copy(enabled = false, focusDepth = -1f, focusX = -1f, focusY = -1f)) }
                _status.update { it.copy(loading = false, analysing = true) }
                depthVersion.value++
                val res = withContext(Dispatchers.Default) { Segmenter.analyse(bmp) }
                analysis = Analysis(res.w, res.h, res.mask, res.faces, res.personFound)
                _status.update { it.copy(analysing = false, personFound = res.personFound, faces = res.faces.size) }
                depthVersion.value++
                buildThumbs(bmp)
            } catch (e: Throwable) {
                _status.update { it.copy(loading = false, analysing = false, message = "Gagal membuka gambar: ${e.message}") }
            }
        }
    }

    private fun buildThumbs(bmp: Bitmap) {
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch(Dispatchers.Default) {
            val s = 150f / max(bmp.width, bmp.height)
            val small = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).toInt()), max(1, (bmp.height * s).toInt()), true)
            val f = Renderer.toFloat(small)
            val a = analysis?.let { an ->
                Analysis(f.w, f.h, Pixels.resize(an.mask, an.w, an.h, f.w, f.h), an.faces, an.personFound)
            }
            val map = HashMap<String, Bitmap>()
            for (look in Looks.all) {
                val b = Renderer.render(f, a, null, EditState(lookId = look.id), PhotoMeta(), withWatermark = false)
                map[look.id] = b
                if (map.size % 6 == 0) _thumbs.value = HashMap(map)
            }
            _thumbs.value = map
        }
    }

    // ------------------------------------------------------------------ suntingan
    fun update(block: (EditState) -> EditState) = _state.update(block)

    fun selectLook(id: String?) = _state.update { it.copy(lookId = id, lookIntensity = 1f) }

    fun resetAll() {
        _state.value = EditState(watermark = _state.value.watermark)
    }

    // ------------------------------------------------------------------ AI Portrait
    fun detectDepth() {
        val src = previewF ?: return
        val an = analysis ?: run { message("Analisis AI belum siap, cuba sebentar lagi."); return }
        viewModelScope.launch {
            _status.update { it.copy(depthBusy = true) }
            val keepAll = _state.value.portrait.keepAllSubjects
            val model = withContext(Dispatchers.Default) {
                val m = if (an.w == src.w && an.h == src.h) an.mask else Pixels.resize(an.mask, an.w, an.h, src.w, src.h)
                DepthEngine.build(src, m, edits, keepAll)
            }
            depth = model
            _depthMap.value = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(DepthEngine.depthPreview(model), model.w, model.h, Bitmap.Config.ARGB_8888)
            }
            _state.update { it.copy(portrait = it.portrait.copy(enabled = true)) }
            _status.update {
                it.copy(depthBusy = false, depthReady = true,
                    message = if (an.personFound) "AI mengesan subjek manusia (${an.faces.size} muka)."
                    else "Tiada manusia dikesan — subjek dianggar dari fokus & kedudukan. Guna berus untuk betulkan.")
            }
            depthVersion.value++
        }
    }

    fun setPortrait(block: (PortraitSettings) -> PortraitSettings) {
        val before = _state.value.portrait
        val after = block(before)
        _state.update { it.copy(portrait = after) }
        if (after.keepAllSubjects != before.keepAllSubjects && depth != null) detectDepth()
    }

    fun tapFocus(nx: Float, ny: Float) {
        val d = depth ?: return
        val fd = DepthEngine.depthAt(d, nx, ny)
        setPortrait { it.copy(focusX = nx, focusY = ny, focusDepth = fd) }
    }

    fun setBrush(mode: Int) = _status.update { it.copy(brush = mode) }
    fun setBrushSize(s: Float) = _status.update { it.copy(brushSize = s) }
    fun toggleDepthMap() = _status.update { it.copy(showDepthMap = !it.showDepthMap) }

    private var brushRebuild: Job? = null

    /** Pembetulan manual topeng subjek dengan berus. */
    fun paint(nx: Float, ny: Float) {
        val src = previewF ?: return
        val mode = _status.value.brush
        if (mode == 0) return
        val e = edits ?: FloatArray(src.w * src.h).also { edits = it }
        val cx = nx * src.w; val cy = ny * src.h
        val r = _status.value.brushSize * max(src.w, src.h)
        val sign = if (mode == 1) 1f else -1f
        val x0 = max(0, (cx - r).toInt()); val x1 = min(src.w - 1, (cx + r).toInt())
        val y0 = max(0, (cy - r).toInt()); val y1 = min(src.h - 1, (cy + r).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / r
            if (d <= 1f) {
                val i = y * src.w + x
                e[i] = (e[i] + sign * (1f - d * d)).coerceIn(-1f, 1f)
            }
        }
        brushRebuild?.cancel()
        brushRebuild = viewModelScope.launch { delay(250); detectDepth() }
    }

    fun clearBrush() { edits = null; if (depth != null) detectDepth() }

    // ------------------------------------------------------------------ eksport
    fun outputSize(opt: ExportOptions): Pair<Int, Int> {
        val p = _photo.value ?: return 0 to 0
        val budget = PhotoIO.pixelBudget()
        var w = p.width; var h = p.height
        val px = w.toLong() * h
        if (px > budget) { val f = sqrt(budget.toDouble() / px); w = (w * f).toInt(); h = (h * f).toInt() }
        if (opt.size == ExportSize.ORIGINAL) return w to h
        val long = opt.size.longEdge
        val f = long.toFloat() / max(p.width, p.height)
        return (p.width * f).roundToInt() to (p.height * f).roundToInt()
    }

    fun export(opt: ExportOptions) {
        val p = _photo.value ?: return
        viewModelScope.launch {
            _status.update { it.copy(exporting = true) }
            try {
                val msg = withContext(Dispatchers.Default) {
                    val budget = PhotoIO.pixelBudget()
                    val full = withContext(Dispatchers.IO) { PhotoIO.decode(ctx, p.uri, budget) }
                    var img = Renderer.toFloat(full)
                    full.recycle()
                    val st = _state.value
                    val d = depth?.let { if (st.portrait.enabled) DepthEngine.scaleTo(it, img) else null }
                    var out = Renderer.render(img, analysis, d, st, p.meta, withWatermark = false)
                    val note = StringBuilder()
                    if (p.width.toLong() * p.height > budget)
                        note.append("Dihadkan ke ${out.width}×${out.height} kerana memori peranti. ")
                    if (opt.size != ExportSize.ORIGINAL) {
                        val (tw, th) = outputSize(opt)
                        if (tw > out.width) {
                            img = Renderer.toFloat(out)
                            out.recycle()
                            val up = Enhance.upscale(img, tw, th)
                            out = Bitmap.createBitmap(up.toArgb(), up.w, up.h, Bitmap.Config.ARGB_8888)
                            note.append("Dibesarkan dari ${p.width}×${p.height} (interpolasi, bukan butiran ${opt.size.label} sebenar). ")
                        } else if (tw < out.width) {
                            val s = Bitmap.createScaledBitmap(out, tw, th, true)
                            out.recycle(); out = s
                        }
                    }
                    val sim = if (st.portrait.enabled) st.portrait.aperture else null
                    val wm = Watermark.draw(out, st.watermark, p.meta, sim)
                    withContext(Dispatchers.IO) { PhotoIO.save(ctx, wm, opt, p.exif) }
                    "Disimpan ${wm.width}×${wm.height} ${opt.format.label} ke Pictures/LeicaFrame. $note"
                }
                _status.update { it.copy(exporting = false, message = msg.trim()) }
                refreshSaved()
            } catch (e: OutOfMemoryError) {
                _status.update { it.copy(exporting = false, message = "Memori tidak cukup — cuba saiz 2K.") }
            } catch (e: Throwable) {
                _status.update { it.copy(exporting = false, message = "Eksport gagal: ${e.message}") }
            }
        }
    }

    fun refreshSaved() {
        viewModelScope.launch(Dispatchers.IO) { _saved.value = PhotoIO.listSaved(ctx) }
    }

    fun looksFor(c: Collection) = Looks.byCollection(c)

    @Suppress("unused")
    private fun norm(v: Float) = clamp01(v)
}
