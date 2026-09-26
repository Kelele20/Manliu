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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ImportResult(val imported: Int, val failed: Int)
data class FolderImage(val uri: Uri, val name: String, val modifiedAt: Long, val sizeBytes: Long)

private class StorageFullException(message: String) : IOException(message)

class ComicRepository private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: ComicRepository? = null

        fun get(context: Context): ComicRepository = instance ?: synchronized(this) {
            instance ?: ComicRepository(context.applicationContext).also { instance = it }
        }
    }

    internal val database = Room.databaseBuilder(
        context.applicationContext,
        ComicDatabase::class.java,
        "comics.db",
    ).addMigrations(MIGRATION_1_2).build()
    internal val dao = database.comicDao()

    val albums: Flow<List<ComicAlbum>> = dao.observeAlbums()
    val overviews: Flow<List<AlbumOverview>> = dao.observeOverviews()

    fun album(id: Long): Flow<ComicAlbum?> = dao.observeAlbum(id)
    fun pages(id: Long): Flow<List<ComicPage>> = dao.observePages(id)
    suspend fun pageSnapshot(id: Long): List<ComicPage> = withContext(Dispatchers.IO) { dao.getPages(id) }
    suspend fun albumSnapshot(id: Long): ComicAlbum? = withContext(Dispatchers.IO) { dao.findAlbum(id) }
    fun latestImport(id: Long): Flow<ImportJob?> = dao.observeLatestImport(id)
    fun failedImports(taskId: Long): Flow<List<ImportItem>> = dao.observeFailedItems(taskId)

    fun imageFile(page: ComicPage): File = File(albumDirectory(page.albumId), page.fileName)
    fun imageFile(albumId: Long, fileName: String): File = File(albumDirectory(albumId), fileName)
    fun freeSpace(): Long = context.filesDir.usableSpace

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
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val images = mutableListOf<FolderImage>()
        val cursor = context.contentResolver.query(children, columns, null, null, null)
            ?: throw IOException("无法读取文件夹")
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
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
                        sizeBytes = if (sizeIndex < 0 || it.isNull(sizeIndex)) -1 else it.getLong(sizeIndex),
                    ),
                )
            }
        }
        images
    }

    suspend fun createFolderImport(albumId: Long, folderUri: Uri, images: List<FolderImage>): Long =
        withContext(Dispatchers.IO) {
            require(images.size in 1..10_000) { "一次最多导入 10000 张" }
            val required = images.sumOf { it.sizeBytes.coerceAtLeast(0) }
            if (required > 0 && required + 64L * 1024 * 1024 > freeSpace()) {
                throw IOException("剩余空间不足，至少需要 ${formatSize(required)}")
            }
            database.withTransaction {
                require(dao.findAlbum(albumId) != null) { "图集不存在" }
                require(dao.unfinishedImport(albumId) == null) { "请先完成或取消上次导入" }
                val taskId = dao.addImportJob(
                    ImportJob(albumId = albumId, folderUri = folderUri.toString(), total = images.size),
                )
                dao.addImportItems(images.mapIndexed { index, image ->
                    ImportItem(
                        taskId = taskId,
                        sequence = index,
                        uri = image.uri.toString(),
                        name = image.name,
                        sizeBytes = image.sizeBytes,
                    )
                })
                taskId
            }
        }

    suspend fun pauseImport(taskId: Long) = withContext(Dispatchers.IO) {
        dao.setImportState(taskId, "PAUSED", "已暂停，可继续导入")
    }

    suspend fun cancelImport(taskId: Long) = withContext(Dispatchers.IO) {
        dao.setImportState(taskId, "CANCELLED", "已停止，已导入的图片保留")
    }

    suspend fun pauseRunningImport(message: String) = withContext(Dispatchers.IO) {
        dao.pauseRunningImports(message)
    }

    suspend fun resumeImport(taskId: Long, retryFailures: Boolean = false) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val task = dao.findImportJob(taskId) ?: return@withTransaction
            if (task.status == "CANCELLED") return@withTransaction
            if (retryFailures) {
                dao.resetFailedItems(taskId)
                dao.resetFailedCounter(taskId)
            } else if (task.processed < task.total) {
                dao.setImportState(taskId, "QUEUED", null)
            } else {
                dao.setImportStatus(taskId, "DONE")
            }
        }
    }

    suspend fun processNextImport(onProgress: (ImportJob) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val task = dao.nextQueuedImport() ?: return@withContext false
        dao.setImportState(task.id, "RUNNING", null)
        var nextPosition = dao.nextPosition(task.albumId)
        while (true) {
            currentCoroutineContext().ensureActive()
            val current = dao.findImportJob(task.id) ?: return@withContext true
            if (current.status != "RUNNING") return@withContext true
            val item = dao.nextPendingItem(task.id) ?: break
            try {
                val filename = "import-${item.id}.${imageExtension(Uri.parse(item.uri), item.name)}"
                val (width, height) = copyAndInspect(task.albumId, Uri.parse(item.uri), filename, item.sizeBytes)
                database.withTransaction {
                    if (dao.findAlbum(task.albumId) == null) throw IOException("图集已删除")
                    if (dao.pageForImportItem(item.id) == null) {
                        dao.addPage(ComicPage(
                            albumId = task.albumId,
                            position = nextPosition,
                            fileName = filename,
                            originalName = item.name,
                            width = width,
                            height = height,
                            importItemId = item.id,
                        ))
                        nextPosition++
                    }
                    dao.markImportItem(item.id, "DONE", null)
                    dao.recordImportResult(task.id, 1, 0)
                }
            } catch (error: StorageFullException) {
                dao.setImportState(task.id, "PAUSED", error.message)
                return@withContext true
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                database.withTransaction {
                    if (dao.findImportJob(task.id) != null) {
                        dao.markImportItem(item.id, "FAILED", error.message?.take(120) ?: "无法读取图片")
                        dao.recordImportResult(task.id, 0, 1)
                    }
                }
            }
            val latest = dao.findImportJob(task.id) ?: return@withContext true
            if (latest.processed % 10 == 0 || latest.processed == latest.total) onProgress(latest)
        }
        val finished = dao.findImportJob(task.id) ?: return@withContext true
        dao.setImportState(task.id, "DONE", if (finished.failed > 0) "有 ${finished.failed} 张导入失败，可查看并重试" else null)
        onProgress(dao.findImportJob(task.id) ?: finished)
        true
    }

    private fun formatSize(bytes: Long): String = "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)

    private suspend fun importOne(albumId: Long, uri: Uri) {
        val originalName = displayName(uri) ?: "图片"
        val filename = "${UUID.randomUUID()}.${imageExtension(uri, originalName)}"
        val (width, height) = copyAndInspect(albumId, uri, filename, -1)
        try {
            dao.addPage(ComicPage(
                albumId = albumId,
                position = dao.nextPosition(albumId),
                fileName = filename,
                originalName = originalName,
                width = width,
                height = height,
            ))
        } catch (error: Exception) {
            imageFile(albumId, filename).delete()
            throw error
        }
    }

    private fun imageExtension(uri: Uri, originalName: String): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        return (if (mime.startsWith("image/")) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()
        } else null) ?: originalName.substringAfterLast('.', "").lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "img"
    }

    private suspend fun copyAndInspect(albumId: Long, uri: Uri, filename: String, sizeBytes: Long): Pair<Int, Int> {
        val resolver = context.contentResolver
        val directory = albumDirectory(albumId).apply { mkdirs() }
        val partial = File(directory, "$filename.part")
        val target = File(directory, filename)
        try {
            if (!target.exists()) {
                val reserve = 32L * 1024 * 1024
                if (directory.usableSpace < sizeBytes.coerceAtLeast(1) + reserve) {
                    throw StorageFullException("存储空间不足；清理空间后点击继续")
                }
                val source = resolver.openInputStream(uri) ?: throw IOException("无法打开图片")
                source.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 100L * 1024 * 1024) throw IOException("图片超过 100 MB")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (!partial.renameTo(target)) throw IOException("保存图片失败")
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            var width = bounds.outWidth
            var height = bounds.outHeight
            if (width <= 0 || height <= 0) throw IOException("无法识别的图片")
            val orientation = try {
                ExifInterface(target.absolutePath).getAttributeInt(
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
            return width to height
        } catch (error: Exception) {
            if (error !is StorageFullException) target.delete()
            if (error is IOException && directory.usableSpace < 32L * 1024 * 1024) {
                throw StorageFullException("存储空间不足；清理空间后点击继续")
            }
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
            val readingPageId = dao.findAlbum(albumId)?.let { album ->
                album.progressPageId.takeIf { it != 0L } ?: current.getOrNull(album.progressPage)?.id
            }
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
                dao.saveProgress(albumId, newIndex, oldOffset, readingPageId)
            }
        }
    }

    suspend fun setChapter(pageId: Long, title: String?) = withContext(Dispatchers.IO) {
        dao.setChapterTitle(pageId, title?.trim()?.take(60)?.ifBlank { null })
    }

    suspend fun deletePage(page: ComicPage) = deletePages(page.albumId, setOf(page.id))

    suspend fun deletePages(albumId: Long, ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val removed = database.withTransaction {
            val all = dao.getPages(albumId)
            val toRemove = all.filter { it.id in ids }
            if (toRemove.isEmpty()) return@withTransaction emptyList<ComicPage>()
            toRemove.map { it.id }.chunked(300).forEach { dao.removePages(albumId, it) }
            val remaining = all.filterNot { it.id in ids }
            remaining.forEachIndexed { index, page ->
                if (page.position != index) dao.changePosition(page.id, index)
            }
            val album = dao.findAlbum(albumId)
            if (album != null && remaining.isNotEmpty()) {
                val oldId = album.progressPageId.takeIf { it != 0L }
                    ?: all.getOrNull(album.progressPage)?.id
                val newIndex = remaining.indexOfFirst { it.id == oldId }
                    .takeIf { it >= 0 } ?: album.progressPage.coerceIn(0, remaining.lastIndex)
                val offset = if (remaining[newIndex].id == oldId) album.progressOffset else 0
                dao.saveProgress(albumId, newIndex, offset, remaining[newIndex].id)
            } else if (album != null) {
                dao.saveProgress(albumId, 0, 0, 0)
            }
            toRemove
        }
        removed.forEach { imageFile(it).delete() }
    }

    suspend fun saveProgress(albumId: Long, index: Int, offset: Int, pageId: Long) =
        withContext(Dispatchers.IO) {
            dao.saveProgress(albumId, index, offset, pageId)
        }

    private fun albumDirectory(id: Long): File = File(context.filesDir, "albums/$id")
}
