package com.kelele.manliu

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ImportResult(val imported: Int, val failed: Int)
data class FolderImage(val uri: Uri, val name: String, val modifiedAt: Long)

class ComicRepository(private val context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        ComicDatabase::class.java,
        "comics.db",
    ).build()
    private val dao = database.comicDao()

    val albums: Flow<List<ComicAlbum>> = dao.observeAlbums()

    fun album(id: Long): Flow<ComicAlbum?> = dao.observeAlbum(id)
    fun pages(id: Long): Flow<List<ComicPage>> = dao.observePages(id)

    fun imageFile(page: ComicPage): File = File(albumDirectory(page.albumId), page.fileName)

    suspend fun createAlbum(title: String): Long = withContext(Dispatchers.IO) {
        dao.addAlbum(ComicAlbum(title = title.trim().take(80)))
    }

    suspend fun deleteAlbum(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.removeAlbum(id) }
        albumDirectory(id).deleteRecursively()
    }

    suspend fun importImages(albumId: Long, uris: List<Uri>): ImportResult =
        withContext(Dispatchers.IO) {
            if (dao.findAlbum(albumId) == null) return@withContext ImportResult(0, uris.size)
            var imported = 0
            var failed = 0
            for (uri in uris) {
                try {
                    importOne(albumId, uri)
                    imported++
                } catch (_: Exception) {
                    failed++
                }
            }
            ImportResult(imported, failed)
        }

    suspend fun listFolderImages(folderUri: Uri): List<FolderImage> = withContext(Dispatchers.IO) {
        val documentId = DocumentsContract.getTreeDocumentId(folderUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val images = mutableListOf<FolderImage>()
        val cursor = context.contentResolver.query(children, columns, null, null, null)
            ?: throw IOException("无法读取文件夹")
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (it.moveToNext()) {
                val type = it.getString(typeIndex) ?: ""
                val name = it.getString(nameIndex) ?: "图片"
                if (type == DocumentsContract.Document.MIME_TYPE_DIR ||
                    (!type.startsWith("image/") && !name.substringAfterLast('.', "")
                        .lowercase().let { ext -> ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif") })
                ) continue
                images.add(
                    FolderImage(
                        uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, it.getString(idIndex)),
                        name = name,
                        modifiedAt = if (modifiedIndex < 0 || it.isNull(modifiedIndex)) 0 else it.getLong(modifiedIndex),
                    ),
                )
            }
        }
        images
    }

    private suspend fun importOne(albumId: Long, uri: Uri) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: ""
        val originalName = displayName(uri) ?: "图片"
        val extension = (if (mime.startsWith("image/")) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()
        } else null) ?: originalName.substringAfterLast('.', "").lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "img"
        val filename = "${UUID.randomUUID()}.$extension"
        val directory = albumDirectory(albumId).apply { mkdirs() }
        val partial = File(directory, "$filename.part")
        val target = File(directory, filename)
        try {
            val source = resolver.openInputStream(uri) ?: throw IOException("无法打开图片")
            source.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 100L * 1024 * 1024) throw IOException("图片超过 100 MB")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(partial.absolutePath, bounds)
            var width = bounds.outWidth
            var height = bounds.outHeight
            if (width <= 0 || height <= 0) throw IOException("无法识别的图片")
            val orientation = try {
                ExifInterface(partial.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } catch (_: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                val oldWidth = width
                width = height
                height = oldWidth
            }
            if (!partial.renameTo(target)) throw IOException("保存图片失败")
            val page = ComicPage(
                albumId = albumId,
                position = dao.nextPosition(albumId),
                fileName = filename,
                originalName = originalName,
                width = width,
                height = height,
            )
            dao.addPage(page)
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            partial.delete()
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.take(100) else null
            }
    } catch (_: Exception) {
        null
    }

    suspend fun movePage(albumId: Long, pageId: Long, direction: Int) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val list = dao.getPages(albumId)
                val index = list.indexOfFirst { it.id == pageId }
                val other = index + direction
                if (index !in list.indices || other !in list.indices) return@withTransaction
                dao.changePosition(list[index].id, list[other].position)
                dao.changePosition(list[other].id, list[index].position)
            }
        }

    suspend fun sortPagesByName(albumId: Long, ascending: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = dao.getPages(albumId)
            val readingPageId = dao.findAlbum(albumId)?.let { current.getOrNull(it.progressPage)?.id }
            val ordered = current.sortedWith { a, b ->
                val comparison = compareImageNames(a.originalName, b.originalName)
                if (comparison == 0) a.id.compareTo(b.id)
                else if (ascending) comparison else -comparison
            }
            ordered.forEachIndexed { position, page ->
                if (page.position != position) dao.changePosition(page.id, position)
            }
            if (readingPageId != null) {
                val newIndex = ordered.indexOfFirst { it.id == readingPageId }
                val oldOffset = dao.findAlbum(albumId)?.progressOffset ?: 0
                dao.saveProgress(albumId, newIndex, oldOffset)
            }
        }
    }

    suspend fun deletePage(page: ComicPage) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.removePage(page.id)
            dao.closeGap(page.albumId, page.position)
        }
        imageFile(page).delete()
    }

    suspend fun saveProgress(albumId: Long, index: Int, offset: Int) =
        withContext(Dispatchers.IO) {
            dao.saveProgress(albumId, index, offset)
        }

    private fun albumDirectory(id: Long): File = File(context.filesDir, "albums/$id")
}
