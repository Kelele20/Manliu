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
import kotlinx.coroutines.CancellationException
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
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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
        database.withTransaction {
            dao.makeRoomForAlbums(1)
            dao.addAlbum(ComicAlbum(title = title.trim().take(80), sortOrder = 0))
        }
    }

    suspend fun reorderAlbums(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = dao.getAlbums()
            require(orderedIds.size == current.size &&
                orderedIds.toSet() == current.map { it.id }.toSet()) { "图集列表已变化，请重试" }
            val positions = current.associate { it.id to it.sortOrder }
            orderedIds.forEachIndexed { index, id ->
                if (positions[id] != index) dao.changeAlbumOrder(id, index)
            }
        }
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
                } catch (error: CancellationException) {
                    throw error
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

    /** Persist the selection before staging so rotation and service restarts retain the task. */
    suspend fun createSelectedImport(albumId: Long, uris: List<Uri>): Long = withContext(Dispatchers.IO) {
        val selected = uris.distinct().take(100)
        require(selected.isNotEmpty()) { "没有选择图片" }
        removeAbandonedStaging()
        val names = selected.mapIndexed { index, uri -> displayName(uri) ?: "图片 ${index + 1}" }
        val batch = UUID.randomUUID().toString()
        database.withTransaction {
            require(dao.findAlbum(albumId) != null) { "图集不存在" }
            require(dao.unfinishedImport(albumId) == null) { "请先完成或取消上次导入" }
            val taskId = dao.addImportJob(ImportJob(
                albumId = albumId, folderUri = "staged:$batch", status = "PREPARING", total = selected.size,
                message = "正在保存所选图片",
            ))
            dao.addImportItems(selected.mapIndexed { index, uri ->
                ImportItem(taskId = taskId, sequence = index, uri = uri.toString(),
                    name = names[index], sizeBytes = -1)
            })
            taskId
        }
    }

    /** Stage transient picker URIs while the foreground service is alive. Already staged items survive restarts. */
    suspend fun prepareNextImport(onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val task = dao.nextPreparingImport() ?: return@withContext false
        val batch = task.folderUri.removePrefix("staged:")
        require(task.folderUri.startsWith("staged:") && batch.matches(Regex("[0-9a-f-]{36}"))) {
            "导入任务来源无效"
        }
        val staging = stagingDirectory(batch).apply { mkdirs() }
        val pending = dao.pendingItems(task.id)
        for ((index, item) in pending.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (dao.findImportJob(task.id)?.status != "PREPARING") return@withContext true
            val file = File(staging, item.sequence.toString())
            val partial = File(staging, "${item.sequence}.part")
            try {
                if (!file.isFile) {
                    partial.delete()
                    val source = context.contentResolver.openInputStream(Uri.parse(item.uri))
                        ?: throw IOException("无法读取 ${item.name}")
                    var size = 0L
                    source.use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                if (size > 100L * 1024 * 1024) throw IOException("${item.name} 超过 100 MB")
                                if (staging.usableSpace < 32L * 1024 * 1024) {
                                    throw StorageFullException("存储空间不足，请清理空间后继续")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (!partial.renameTo(file)) throw IOException("无法保存 ${item.name}")
                }
                if (dao.findImportJob(task.id)?.status != "PREPARING") return@withContext true
                dao.updateImportItemSource(item.id, Uri.fromFile(file).toString(), file.length())
            } catch (error: CancellationException) {
                throw error
            } catch (error: StorageFullException) {
                dao.pauseActiveImport(task.id, error.message ?: "存储空间不足")
                return@withContext true
            } catch (error: Exception) {
                database.withTransaction {
                    if (dao.findImportJob(task.id)?.status == "PREPARING") {
                        dao.markImportItem(item.id, "FAILED", error.message?.take(120) ?: "无法读取图片")
                        dao.recordImportResult(task.id, 0, 1)
                    }
                }
            } finally {
                partial.delete()
            }
            val prepared = index + 1
            if (dao.findImportJob(task.id)?.status == "PREPARING") {
                dao.setImportMessage(task.id, "正在保存所选图片 $prepared / ${pending.size}")
                onProgress(prepared, pending.size)
            }
        }
        dao.finishPreparing(task.id)
        true
    }

    private fun stagingDirectory(batch: String): File = File(context.filesDir, "import-staging/$batch")

    private suspend fun removeAbandonedStaging() {
        val referenced = dao.stagedImportSources().map { it.removePrefix("staged:") }.toSet()
        File(context.filesDir, "import-staging").listFiles()?.forEach { directory ->
            if (directory.isDirectory && directory.name !in referenced) directory.deleteRecursively()
        }
    }

    private fun removeStagedFiles(job: ImportJob) {
        val batch = job.folderUri.removePrefix("staged:")
        if (job.folderUri.startsWith("staged:") && batch.matches(Regex("[0-9a-f-]{36}"))) {
            stagingDirectory(batch).deleteRecursively()
        }
    }

    private fun removeStagedItem(job: ImportJob, item: ImportItem) {
        val batch = job.folderUri.removePrefix("staged:")
        if (job.folderUri.startsWith("staged:") && batch.matches(Regex("[0-9a-f-]{36}"))) {
            val directory = stagingDirectory(batch).canonicalFile
            val file = Uri.parse(item.uri).path?.let { File(it).canonicalFile }
            if (file?.parentFile == directory) file.delete()
        }
    }

    suspend fun pauseImport(taskId: Long) = withContext(Dispatchers.IO) {
        dao.pauseActiveImport(taskId, "已暂停，可继续导入")
    }

    suspend fun cancelImport(taskId: Long) = withContext(Dispatchers.IO) {
        if (dao.cancelActiveImport(taskId, "已停止，已导入的图片保留") > 0) {
            dao.findImportJob(taskId)?.let(::removeStagedFiles)
        }
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
                if (task.folderUri.startsWith("staged:")) dao.setImportState(taskId, "PREPARING", null)
            } else if (task.processed < task.total) {
                dao.setImportState(taskId, if (task.folderUri.startsWith("staged:")) "PREPARING" else "QUEUED", null)
            } else {
                dao.setImportStatus(taskId, "DONE")
            }
        }
    }

    suspend fun processNextImport(onProgress: (ImportJob) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val task = dao.nextQueuedImport() ?: return@withContext false
        if (dao.claimImportJob(task.id) == 0) return@withContext true
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
                    if (dao.findImportJob(task.id)?.status != "RUNNING") {
                        imageFile(task.albumId, filename).delete()
                        return@withTransaction
                    }
                    if (dao.pageForImportItem(item.id) == null) {
                        val appendPosition = dao.nextPosition(task.albumId)
                        val position = importInsertionPosition(
                            dao.nextImportedPosition(task.id, item.sequence),
                            dao.previousImportedPosition(task.id, item.sequence),
                            appendPosition,
                        )
                        if (position < appendPosition) {
                            dao.makeRoomForPage(task.albumId, position)
                            dao.shiftReadingPositionAfterInsert(task.albumId, position)
                        }
                        dao.addPage(ComicPage(
                            albumId = task.albumId,
                            position = position,
                            fileName = filename,
                            originalName = item.name,
                            width = width,
                            height = height,
                            importItemId = item.id,
                        ))
                    }
                    dao.markImportItem(item.id, "DONE", null)
                    dao.recordImportResult(task.id, 1, 0)
                }
                if (dao.pageForImportItem(item.id) != null) removeStagedItem(task, item)
            } catch (error: StorageFullException) {
                dao.pauseActiveImport(task.id, error.message ?: "存储空间不足")
                return@withContext true
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                database.withTransaction {
                    if (dao.findImportJob(task.id)?.status == "RUNNING") {
                        dao.markImportItem(item.id, "FAILED", error.message?.take(120) ?: "无法读取图片")
                        dao.recordImportResult(task.id, 0, 1)
                    }
                }
            }
            val latest = dao.findImportJob(task.id) ?: return@withContext true
            if (latest.processed % 10 == 0 || latest.processed == latest.total) onProgress(latest)
        }
        val finished = dao.findImportJob(task.id) ?: return@withContext true
        if (dao.completeRunningImport(task.id, if (finished.failed > 0) "有 ${finished.failed} 张导入失败，可查看并重试" else null) > 0 && finished.failed == 0) {
            removeStagedFiles(task)
        }
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

    suspend fun reorderPage(albumId: Long, movedId: Long, orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = dao.getPages(albumId)
            val currentIds = current.map { it.id }
            val oldIndex = currentIds.indexOf(movedId)
            val newIndex = orderedIds.indexOf(movedId)
            require(oldIndex >= 0 && newIndex >= 0 && orderedIds.size == currentIds.size &&
                currentIds.toMutableList().apply { add(newIndex, removeAt(oldIndex)) } == orderedIds
            ) { "图片列表已变化，请重试" }
            if (oldIndex == newIndex) return@withTransaction
            // Older databases may have gaps in position after page deletion.
            current.forEachIndexed { index, page ->
                if (page.position != index) dao.changePosition(page.id, index)
            }
            if (oldIndex > newIndex) dao.shiftPagesDown(albumId, newIndex, oldIndex)
            else dao.shiftPagesUp(albumId, oldIndex, newIndex)
            dao.changePosition(movedId, newIndex)

            val album = dao.findAlbum(albumId) ?: return@withTransaction
            val readingId = album.progressPageId.takeIf { it != 0L }
                ?: current.getOrNull(album.progressPage)?.id
            readingId?.let { id ->
                val index = orderedIds.indexOf(id)
                if (index >= 0) dao.saveProgress(albumId, index, album.progressOffset, id)
            }
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
