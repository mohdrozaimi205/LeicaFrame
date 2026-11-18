package com.mohdrozaimi.leicaframe.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palet "bilik gelap": hitam, arang, kelabu gelap, teks putih + aksen lampu keselamatan ambar.
object LF {
    val Black = Color(0xFF000000)
    val Charcoal = Color(0xFF111113)
    val Graphite = Color(0xFF1B1B1E)
    val Line = Color(0xFF2A2A2E)
    val Mute = Color(0xFF8A8A90)
    val White = Color(0xFFF4F4F2)
    val Amber = Color(0xFFE8A33D)
}

val LFColors = darkColorScheme(
    primary = LF.Amber, onPrimary = LF.Black,
    background = LF.Black, onBackground = LF.White,
    surface = LF.Charcoal, onSurface = LF.White,
    surfaceVariant = LF.Graphite, onSurfaceVariant = LF.Mute,
    outline = LF.Line,
)

val Mono = FontFamily.Monospace

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LF.Mute) {
    Text(text.uppercase(), modifier = modifier, color = color,
        style = TextStyle(fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium))
}

@Composable
fun SectionTitle(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(1.dp).background(LF.Amber))
        Spacer(Modifier.width(8.dp))
        Label(text, color = LF.White)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(LF.Line))
    }
}

@Composable
fun LfSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    display: (Float) -> String = { v -> (if (v > 0) "+" else "") + (v * 100).toInt() },
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = LF.White, fontSize = 13.sp)
            Text(display(value), color = if (value != 0f) LF.Amber else LF.Mute, fontFamily = Mono, fontSize = 12.sp,
                modifier = Modifier.clickable { onChange(if (range.start < 0f) 0f else range.start) })
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(thumbColor = LF.White, activeTrackColor = LF.Amber, inactiveTrackColor = LF.Line),
        )
    }
}

@Composable
fun LfSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = LF.White, fontSize = 14.sp)
            if (sub != null) Text(sub, color = LF.Mute, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = LF.Amber, checkedThumbColor = LF.Black))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, if (selected) LF.Amber else LF.Line, RoundedCornerShape(2.dp))
            .background(if (selected) LF.Amber.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) LF.Amber else LF.White, fontSize = 12.sp, letterSpacing = 0.5.sp)
    }
}

/**
 * Pratonton gambar: tekan & tahan = gambar asal (sebelum), lepas = selepas.
 * Menyokong ketik (fokus) dan seret (berus) dengan koordinat dinormalkan pada imej.
 */
@Composable
fun PhotoPreview(
    after: Bitmap?,
    before: Bitmap?,
    modifier: Modifier = Modifier,
    overlay: Bitmap? = null,
    focusPoint: Offset? = null,
    onTap: ((Float, Float) -> Unit)? = null,
    onDrag: ((Float, Float) -> Unit)? = null,
    busy: Boolean = false,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    var showBefore by remember { mutableStateOf(false) }
    val shown = if (showBefore) before else after
    fun toImage(p: Offset, bmp: Bitmap?): Offset? {
        if (bmp == null || box.width == 0) return null
        val s = minOf(box.width / bmp.width.toFloat(), box.height / bmp.height.toFloat())
        val iw = bmp.width * s; val ih = bmp.height * s
        val ox = (box.width - iw) / 2f; val oy = (box.height - ih) / 2f
        val nx = (p.x - ox) / iw; val ny = (p.y - oy) / ih
        if (nx < 0f || ny < 0f || nx > 1f || ny > 1f) return null
        return Offset(nx, ny)
    }
    Box(
        modifier
            .background(LF.Charcoal)
            .onSizeChanged { box = it }
            .pointerInput(onTap, onDrag, before) {
                detectTapGestures(
                    onPress = {
                        if (onDrag == null) { showBefore = true; tryAwaitRelease(); showBefore = false }
                    },
                    onTap = { p -> toImage(p, after ?: before)?.let { onTap?.invoke(it.x, it.y) } },
                )
            }
            .then(if (onDrag != null) Modifier.pointerInput(onDrag) {
                detectDragGestures { change, _ ->
                    toImage(change.position, after ?: before)?.let { onDrag(it.x, it.y) }
                }
            } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (shown != null) {
            Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (overlay != null && !showBefore)
                Image(overlay.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alpha = 0.85f)
            val bmp = after ?: before
            if (focusPoint != null && bmp != null && !showBefore) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = minOf(size.width / bmp.width, size.height / bmp.height)
                    val iw = bmp.width * s; val ih = bmp.height * s
                    val c = Offset((size.width - iw) / 2f + focusPoint.x * iw, (size.height - ih) / 2f + focusPoint.y * ih)
                    drawCircle(LF.Amber, radius = 26.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                    drawCircle(LF.Amber, radius = 3.dp.toPx(), center = c)
                }
            }
        } else {
            Label("Tiada gambar — pilih dari HOME")
        }
        Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
            Label(if (showBefore) "BEFORE" else if (busy) "PROCESSING…" else "AFTER · hold to compare",
                color = if (busy) LF.Amber else LF.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LF.Black).padding(horizontal = 18.dp)) { content() }
}

@Suppress("unused")
@Composable
fun LfTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = LFColors, content = content)
