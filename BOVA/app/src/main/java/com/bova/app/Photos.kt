package com.bova.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** Foto catatan disalin ke penyimpanan aplikasi, diperkecil (maks 1280px) dan dikompres JPEG. */
object PhotoStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun save(ctx: Context, uri: Uri, quality: Int, maxSide: Int = 1280): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (raw == null) null else {
            val rotation = try {
                ctx.contentResolver.openInputStream(uri)?.use { s ->
                    when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) { 0f }
            val scale = min(1f, maxSide.toFloat() / max(raw.width, raw.height))
            val m = Matrix().apply { postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
            val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            val f = File(dir(ctx), "n_${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            Uri.fromFile(f).toString()
        }
    } catch (_: Exception) { null }

    fun delete(uriStr: String?) {
        if (uriStr == null || !uriStr.startsWith("file:")) return
        try { Uri.parse(uriStr).path?.let { File(it).delete() } } catch (_: Exception) {}
    }

    fun totalBytes(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    /** Hapus file foto yang tidak dipakai catatan mana pun. Mengembalikan jumlah byte yang dibebaskan. */
    fun cleanup(ctx: Context, used: Set<String>): Long {
        var freed = 0L
        dir(ctx).listFiles()?.forEach { f ->
            if (Uri.fromFile(f).toString() !in used) { freed += f.length(); f.delete() }
        }
        return freed
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", b / 1_048_576.0)
    b >= 1024 -> "${b / 1024} KB"
    else -> "$b B"
}
