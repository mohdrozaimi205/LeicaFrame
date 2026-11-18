package com.mohdrozaimi.leicaframe.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.mohdrozaimi.leicaframe.watermark.PhotoMeta
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ExportSize(val label: String, val longEdge: Int) {
    ORIGINAL("Original", 0), TWO_K("2K", 2048), FOUR_K("4K", 3840)
}

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    JPEG("JPEG", "image/jpeg", "jpg"), PNG("PNG", "image/png", "png")
}

data class ExportOptions(
    val size: ExportSize = ExportSize.ORIGINAL,
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 100,
    val keepExif: Boolean = true,
)

data class SourceInfo(val width: Int, val height: Int, val meta: PhotoMeta, val exif: ExifInterface?)

object PhotoIO {

    const val FOLDER = "LeicaFrame"

    /** Bajet piksel ikut memori sebenar peranti (≈ 44 bait/piksel semasa pemprosesan). */
    fun pixelBudget(): Long {
        val max = Runtime.getRuntime().maxMemory()
        return (max * 0.70 / 44.0).toLong().coerceIn(4_000_000L, 50_000_000L)
    }

    fun readInfo(ctx: Context, uri: Uri): SourceInfo {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        val exif = try { ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } } catch (e: Exception) { null }
        val rot = exif?.rotationDegrees ?: 0
        val (w, h) = if (rot == 90 || rot == 270) o.outHeight to o.outWidth else o.outWidth to o.outHeight
        return SourceInfo(w, h, readMeta(exif), exif)
    }

    private fun readMeta(e: ExifInterface?): PhotoMeta {
        if (e == null) return PhotoMeta()
        val fn = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        val exp = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        val iso = e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        val fl35 = e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        val fl = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val date = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
        return PhotoMeta(
            make = e.getAttribute(ExifInterface.TAG_MAKE),
            model = e.getAttribute(ExifInterface.TAG_MODEL),
            lens = e.getAttribute(ExifInterface.TAG_LENS_MODEL),
            aperture = if (fn > 0) "f/" + ((fn * 10).roundToInt() / 10.0) else null,
            shutter = if (exp > 0) (if (exp >= 1) "${(exp * 10).roundToInt() / 10.0}s" else "1/${(1 / exp).roundToInt()}s") else null,
            iso = iso?.let { "ISO $it" },
            focal = when { fl35 > 0 -> "${fl35}mm"; fl > 0 -> "${fl.roundToInt()}mm"; else -> null },
            date = date?.let { d -> d.take(10).replace(':', '.') + (if (d.length >= 16) "  " + d.substring(11, 16) else "") },
        )
    }

    /** Nyahkod dengan orientasi betul. maxPixels membataskan memori (0 = had bajet). */
    fun decode(ctx: Context, uri: Uri, maxPixels: Long): Bitmap {
        val info = readInfo(ctx, uri)
        val total = info.width.toLong() * info.height
        var sample = 1
        while (total / (sample.toLong() * sample) > maxPixels * 4) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
            ?: error("Tidak dapat membaca gambar")
        val rot = info.exif?.rotationDegrees ?: 0
        val flip = info.exif?.isFlipped ?: false
        if (rot != 0 || flip) {
            val m = Matrix().apply { if (flip) postScale(-1f, 1f); postRotate(rot.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val px = bmp.width.toLong() * bmp.height
        if (px > maxPixels) {
            val f = sqrt(maxPixels.toDouble() / px)
            bmp = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * f).toInt()), max(1, (bmp.height * f).toInt()), true)
        }
        if (bmp.config != Bitmap.Config.ARGB_8888) bmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
        return bmp
    }

    fun save(ctx: Context, bmp: Bitmap, opt: ExportOptions, sourceExif: ExifInterface?): Uri {
        val name = "LF_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + "." + opt.format.ext
        val cf = if (opt.format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val uri: Uri
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, opt.format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: error("Gagal mencipta fail")
            ctx.contentResolver.openOutputStream(uri)!!.use { write(bmp, cf, opt.quality, it) }
            if (opt.format == ExportFormat.JPEG && opt.keepExif) copyExif(ctx, uri, sourceExif, bmp)
            cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0)
            ctx.contentResolver.update(uri, cv, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
            dir.mkdirs()
            val f = File(dir, name)
            FileOutputStream(f).use { write(bmp, cf, opt.quality, it) }
            if (opt.format == ExportFormat.JPEG && opt.keepExif) copyExifFile(f, sourceExif, bmp)
            MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(opt.format.mime), null)
            uri = Uri.fromFile(f)
        }
        return uri
    }

    private fun write(bmp: Bitmap, cf: Bitmap.CompressFormat, q: Int, os: OutputStream) {
        bmp.compress(cf, q.coerceIn(1, 100), os)
    }

    private val exifTags = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
    )

    private fun fill(dst: ExifInterface, src: ExifInterface?, bmp: Bitmap) {
        if (src != null) for (t in exifTags) src.getAttribute(t)?.let { dst.setAttribute(t, it) }
        dst.setAttribute(ExifInterface.TAG_SOFTWARE, "LEICA FRAME (edited)")
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, bmp.width.toString())
        dst.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, bmp.height.toString())
        dst.saveAttributes()
    }

    private fun copyExif(ctx: Context, uri: Uri, src: ExifInterface?, bmp: Bitmap) {
        try {
            ctx.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd -> fill(ExifInterface(pfd.fileDescriptor), src, bmp) }
        } catch (_: Exception) { }
    }

    private fun copyExifFile(f: File, src: ExifInterface?, bmp: Bitmap) {
        try { fill(ExifInterface(f.absolutePath), src, bmp) } catch (_: Exception) { }
    }

    data class SavedPhoto(val uri: Uri, val name: String, val width: Int, val height: Int, val date: Long)

    fun listSaved(ctx: Context): List<SavedPhoto> {
        val out = ArrayList<SavedPhoto>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.DATE_ADDED,
        )
        val sel: String; val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?"; args = arrayOf("%Pictures/$FOLDER%")
        } else {
            @Suppress("DEPRECATION")
            sel = MediaStore.Images.Media.DATA + " LIKE ?"; args = arrayOf("%/$FOLDER/%")
        }
        try {
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += SavedPhoto(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        c.getString(1) ?: "", c.getInt(2), c.getInt(3), c.getLong(4),
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    fun thumbnail(ctx: Context, uri: Uri, size: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, android.util.Size(size, size), null)
        else decode(ctx, uri, (size * size).toLong())
    } catch (_: Exception) { null }
}
