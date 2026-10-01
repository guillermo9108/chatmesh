package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageMediaUtil {

    // Resolución y calidad pensadas para la malla P2P: imágenes pequeñas
    // y rápidas de transferir sin perder legibilidad.
    private const val DEFAULT_MAX_DIM = 640
    private const val DEFAULT_QUALITY = 60

    fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
        return file
    }

    fun bitmapToBase64(
        bitmap: Bitmap,
        maxDim: Int = DEFAULT_MAX_DIM,
        quality: Int = DEFAULT_QUALITY
    ): String {
        var scaled = bitmap
        val width = bitmap.width
        val height = bitmap.height
        if (width > maxDim || height > maxDim) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth = if (width > height) maxDim else (maxDim * ratio).toInt()
            val newHeight = if (height >= width) maxDim else (maxDim / ratio).toInt()
            scaled = Bitmap.createScaledBitmap(
                bitmap,
                newWidth.coerceAtLeast(1),
                newHeight.coerceAtLeast(1),
                true
            )
        }
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun uriToBase64(
        context: Context,
        uri: Uri,
        maxDim: Int = DEFAULT_MAX_DIM,
        quality: Int = DEFAULT_QUALITY
    ): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            // Corregir rotación según EXIF
            val correctedBitmap = try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotateBitmap(originalBitmap, orientation)
                } ?: originalBitmap
            } catch (_: Exception) {
                originalBitmap
            }

            bitmapToBase64(correctedBitmap, maxDim, quality)
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}