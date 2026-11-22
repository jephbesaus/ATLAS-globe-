package com.atlasglobe.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object ImageUtil {
    /** Copie une image choisie en JPEG réduit dans le stockage interne : léger (important pour le mesh). */
    fun saveScaled(ctx: Context, uri: Uri, maxDim: Int, dest: File, quality: Int = 82): String? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > maxDim * 2 || opts.outHeight / sample > maxDim * 2) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            dest.absolutePath
        } catch (e: Exception) { null }
    }

    fun saveProfilePhoto(ctx: Context, uri: Uri): String? =
        saveScaled(ctx, uri, 512, File(ctx.filesDir, "profile.jpg"), 85)

    fun decodeScaled(path: String?, maxPx: Int): Bitmap? {
        if (path == null) return null
        return runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (o.outWidth / s > maxPx * 2 || o.outHeight / s > maxPx * 2) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        }.getOrNull()
    }

    fun loadBitmap(path: String?): Bitmap? = decodeScaled(path, 512)

    /** Enregistre dans la galerie (Pictures/Atlas). */
    fun saveToGallery(ctx: Context, path: String): Boolean = try {
        val name = "Atlas_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Atlas")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { o -> File(path).inputStream().use { it.copyTo(o) } }
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Atlas").apply { mkdirs() }
            File(path).copyTo(File(dir, name), overwrite = true)
        }
        true
    } catch (e: Exception) { false }
}
