package com.kelele.manliu

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ArchiveProgress(
    val label: String,
    val processed: Int,
    val total: Int,
    val running: Boolean,
    val error: String? = null,
)

object ArchiveStatus {
    private val mutable = MutableStateFlow<ArchiveProgress?>(null)
    val progress = mutable.asStateFlow()
    fun update(value: ArchiveProgress?) { mutable.value = value }
}

/** A .manliu file is a ZIP archive containing our own versioned manifest. */
class ArchiveManager(private val context: Context, private val repository: ComicRepository) {
    suspend fun export(uri: Uri, albumId: Long?, progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val albums = if (albumId == null) repository.dao.getAlbums()
        else listOfNotNull(repository.dao.findAlbum(albumId))
        if (albums.isEmpty()) throw IOException("没有可备份的图集")
        val pages = albums.map { repository.dao.getPages(it.id) }
        val total = pages.sumOf { it.size }
        val manifest = JSONObject().put("format", "manliu-backup").put("version", 1)
        val albumArray = JSONArray()
        albums.forEachIndexed { albumIndex, album ->
            val pageArray = JSONArray()
            pages[albumIndex].forEachIndexed { pageIndex, page ->
                pageArray.put(JSONObject()
                    .put("entry", "a${albumIndex}/p${pageIndex}")
                    .put("name", page.originalName)
                    .put("chapter", page.chapterTitle ?: JSONObject.NULL))
            }
            albumArray.put(JSONObject()
                .put("title", album.title)
                .put("progress", album.progressPage)
                .put("offset", album.progressOffset)
                .put("pages", pageArray))
        }
        manifest.put("albums", albumArray)
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("无法写入备份文件")
        ZipOutputStream(BufferedOutputStream(output, 128 * 1024)).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            var completed = 0
            progress(0, total)
            albums.forEachIndexed { albumIndex, _ ->
                pages[albumIndex].forEachIndexed { pageIndex, page ->
                    currentCoroutineContext().ensureActive()
                    val file = repository.imageFile(page)
                    if (!file.isFile) throw IOException("找不到图片：${page.originalName}")
                    zip.putNextEntry(ZipEntry("a${albumIndex}/p${pageIndex}"))
                    file.inputStream().buffered().use { it.copyTo(zip, 128 * 1024) }
                    zip.closeEntry()
                    completed++
                    if (completed % 10 == 0 || completed == total) progress(completed, total)
                }
            }
        }
    }

    suspend fun restore(uri: Uri, progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val base = File(context.filesDir, "restore-staging").apply { mkdirs() }
        // A previous process may have stopped before it could remove temporary files.
        base.listFiles()?.forEach { it.deleteRecursively() }
        val staging = File(base, UUID.randomUUID().toString()).apply { mkdirs() }
        val moved = mutableListOf<File>()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取备份文件")
            val stagedAlbums = ZipInputStream(BufferedInputStream(input, 128 * 1024)).use { zip ->
                if (zip.nextEntry?.name != "manifest.json") throw IOException("不是漫流备份文件")
                val manifest = JSONObject(String(readManifest(zip), Charsets.UTF_8))
                if (manifest.optString("format") != "manliu-backup" || manifest.optInt("version") != 1) {
                    throw IOException("备份格式不受支持")
                }
                zip.closeEntry()
                val albums = manifest.getJSONArray("albums")
                if (albums.length() !in 1..1000) throw IOException("备份图集数量异常")
                val total = (0 until albums.length()).sumOf { albums.getJSONObject(it).getJSONArray("pages").length() }
                if (total > 200_000) throw IOException("备份图片数量异常")
                progress(0, total)
                var completed = 0
                val result = mutableListOf<StagedAlbum>()
                repeat(albums.length()) { albumIndex ->
                    val album = albums.getJSONObject(albumIndex)
                    val pageItems = album.getJSONArray("pages")
                    val directory = File(staging, "a$albumIndex").apply { mkdirs() }
                    val stagedPages = mutableListOf<StagedPage>()
                    repeat(pageItems.length()) { pageIndex ->
                        currentCoroutineContext().ensureActive()
                        val meta = pageItems.getJSONObject(pageIndex)
                        val expected = "a${albumIndex}/p${pageIndex}"
                        if (meta.getString("entry") != expected || zip.nextEntry?.name != expected) {
                            throw IOException("备份图片顺序不正确")
                        }
                        val name = meta.optString("name", "图片").take(100)
                        val extension = name.substringAfterLast('.', "").lowercase()
                            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "img"
                        val image = File(directory, "${pageIndex}.$extension")
                        writeImage(zip, image)
                        zip.closeEntry()
                        val (width, height) = imageBounds(image)
                        stagedPages.add(StagedPage(
                            image.name, name,
                            if (meta.isNull("chapter")) null else meta.getString("chapter").take(60).ifBlank { null },
                            width, height,
                        ))
                        completed++
                        if (completed % 10 == 0 || completed == total) progress(completed, total)
                    }
                    result.add(StagedAlbum(
                        album.optString("title", "恢复的图集").take(80).ifBlank { "恢复的图集" },
                        album.optInt("progress"), album.optInt("offset"), directory, stagedPages,
                    ))
                }
                if (zip.nextEntry != null) throw IOException("备份文件包含未预期的内容")
                result
            }
            repository.database.withTransaction {
                stagedAlbums.forEach { album ->
                    val newId = repository.dao.addAlbum(ComicAlbum(title = album.title))
                    val target = File(context.filesDir, "albums/$newId")
                    target.parentFile?.mkdirs()
                    if (!album.directory.renameTo(target)) throw IOException("无法保存恢复的图集")
                    moved.add(target)
                    val pageIds = album.pages.mapIndexed { index, page ->
                        repository.dao.addPage(ComicPage(
                            albumId = newId, position = index, fileName = page.fileName,
                            originalName = page.originalName, width = page.width, height = page.height,
                            chapterTitle = page.chapter,
                        ))
                    }
                    if (pageIds.isNotEmpty()) {
                        val savedPage = album.progress.coerceIn(0, pageIds.lastIndex)
                        repository.dao.saveProgress(newId, savedPage, album.offset.coerceAtLeast(0), pageIds[savedPage])
                    }
                }
            }
        } catch (error: Exception) {
            // The database transaction rolls back; remove any already-moved image folders.
            moved.forEach { it.deleteRecursively() }
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun readManifest(zip: ZipInputStream): ByteArray {
        val limit = 64 * 1024 * 1024
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IOException("备份目录过大")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun writeImage(zip: ZipInputStream, destination: File) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        destination.outputStream().buffered().use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = zip.read(buffer)
                if (count < 0) break
                total += count
                if (total > 100L * 1024 * 1024) throw IOException("备份中单张图片超过 100 MB")
                output.write(buffer, 0, count)
            }
        }
    }

    private fun imageBounds(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var width = bounds.outWidth
        var height = bounds.outHeight
        if (width <= 0 || height <= 0) throw IOException("备份包含损坏的图片")
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )
        if (orientation in listOf(
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
            )
        ) {
            val previous = width
            width = height
            height = previous
        }
        return width to height
    }

    private data class StagedPage(
        val fileName: String, val originalName: String, val chapter: String?,
        val width: Int, val height: Int,
    )
    private data class StagedAlbum(
        val title: String, val progress: Int, val offset: Int, val directory: File,
        val pages: List<StagedPage>,
    )
}
