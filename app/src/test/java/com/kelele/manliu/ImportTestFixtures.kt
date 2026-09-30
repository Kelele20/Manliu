package com.kelele.manliu

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

internal class BlockingImportProvider(private val file: File, private val mimeType: String = "application/zip") : ContentProvider() {
    val release = CountDownLatch(1)
    val blocked = CountDownLatch(1)
    override fun onCreate() = true
    override fun getType(uri: Uri) = mimeType
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    fun register(context: Context, uri: Uri) {
        attachInfo(context, ProviderInfo().apply { authority = uri.authority })
        ShadowContentResolver.registerProviderInternal(uri.authority!!, this)
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { openStream() }
    }

    private fun openStream(): InputStream = object : FilterInputStream(file.inputStream()) {
        var consumed = 0L
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (consumed >= 512 * 1024) {
                blocked.countDown()
                if (!release.await(30, TimeUnit.SECONDS)) throw java.io.IOException("test input barrier timed out")
            }
            val count = super.read(bytes, offset, minOf(length, 8192))
            if (count > 0) consumed += count
            return count
        }
    }
}

/** A valid PNG large enough to block its reader midway through preparation. */
internal fun largeTestPng(): ByteArray {
    val random = java.util.Random(42)
    val pixels = IntArray(1024 * 1024) { random.nextInt() or 0xff000000.toInt() }
    val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, 1024, 0, 0, 1024, 1024)
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    bitmap.recycle()
    return output.toByteArray()
}
