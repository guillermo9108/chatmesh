package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageMediaUtil {

    fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file
    }

    fun bitmapToBase64(bitmap: Bitmap, maxDim: Int = 800, quality: Int = 75): String {
        var scaled = bitmap
        val width = bitmap.width
        val height = bitmap.height
        if (width > maxDim || height > maxDim) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth = if (width > height) maxDim else (maxDim * ratio).toInt()
            val newHeight = if (height >= width) maxDim else (maxDim / ratio).toInt()
            scaled = Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun uriToBase64(context: Context, uri: Uri, maxDim: Int = 800, quality: Int = 75): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap != null) {
                bitmapToBase64(originalBitmap, maxDim, quality)
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
