package com.kelele.manliu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchiveImportIntegrationTest {
    private lateinit var context: Context
    private lateinit var repository: ComicRepository
    private var albumId: Long = 0

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        ComicRepository::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        context.deleteDatabase("comics.db")
        context.filesDir.deleteRecursively()
        context.filesDir.mkdirs()
        repository = ComicRepository.get(context)
        albumId = repository.createAlbum("验证图集")
    }
    @After fun teardown() {
        repository.database.close()
        ComicRepository::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        context.filesDir.deleteRecursively()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
    private fun zip(entries: List<Pair<String, ByteArray>>): File {
        val file = File(context.cacheDir, "input.cbz")
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry()
            }
        }
        return file
    }
    @Test fun oversizedZipEntryMustBeRejectedBeforeItIsFullyStaged() = runBlocking {
        val file = File(context.cacheDir, "large.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("oversized.jpg"))
            val block = ByteArray(1024 * 1024)
            repeat(101) { out.write(block) }
            out.closeEntry()
        }
        val jobId = repository.createArchiveImport(albumId, Uri.fromFile(file))
        repository.prepareNextImport { _, _ -> }
        val job = repository.dao.findImportJob(jobId)!!
        val rejected = job.status == "PAUSED" && job.message?.contains("100 MiB") == true
        val stagedBytes = File(context.filesDir, "import-staging").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        println("EVIDENCE zip_limit compressed=${file.length()} staged=$stagedBytes rejected=$rejected")
        assertTrue("解压阶段应拒绝大于 100 MiB 的条目，实际暂存 $stagedBytes 字节", rejected)
        assertEquals(0L, stagedBytes)
        assertTrue(repository.pageSnapshot(albumId).isEmpty())
    }
    @Test fun chapterDirectoriesMustKeepTheirReadingOrder() = runBlocking {
        val colors = listOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffff00.toInt())
        val file = zip(listOf("chapter1/1.png", "chapter1/2.png", "chapter2/1.png", "chapter2/2.png")
            .mapIndexed { index, name -> name to png(colors[index]) })
        repository.createArchiveImport(albumId, Uri.fromFile(file))
        assertTrue(repository.prepareNextImport { _, _ -> })
        assertTrue(repository.processNextImport {})
        val pages = repository.pageSnapshot(albumId)
        val actual = pages.map { BitmapFactory.decodeFile(repository.imageFile(it).absolutePath).getPixel(0, 0) }
        println("EVIDENCE chapter_order expected=$colors actual=$actual names=${pages.map { it.originalName }}")
        assertEquals("不同章节应按目录顺序阅读", colors, actual)
    }
    @Test fun deletingAlbumMustRemoveItsPausedImportStaging() = runBlocking {
        val file = zip(listOf("1.png" to png(0xffff0000.toInt()), "2.png" to png(0xff00ff00.toInt())))
        val jobId = repository.createArchiveImport(albumId, Uri.fromFile(file))
        assertTrue(repository.prepareNextImport { _, _ -> })
        repository.pauseImport(jobId)
        val before = File(context.filesDir, "import-staging").walkTopDown().count { it.isFile }
        repository.deleteAlbum(albumId)
        val after = File(context.filesDir, "import-staging").walkTopDown().count { it.isFile }
        println("EVIDENCE staging_cleanup before=$before after=$after job=${repository.dao.findImportJob(jobId)} album=${repository.albumSnapshot(albumId)}")
        assertEquals(2, before)
        assertNull(repository.dao.findImportJob(jobId))
        assertNull(repository.albumSnapshot(albumId))
        assertEquals("删除图集后应清理其暂存文件", 0, after)
    }
    @Test fun normalSingleChapterImportWorksAndCleansStaging() = runBlocking {
        val file = zip(listOf("2.png" to png(0xff00ff00.toInt()), "1.png" to png(0xffff0000.toInt())))
        val jobId = repository.createArchiveImport(albumId, Uri.fromFile(file))
        assertTrue(repository.prepareNextImport { _, _ -> })
        repository.processNextImport {}
        assertEquals(listOf("1.png", "2.png"), repository.pageSnapshot(albumId).map { it.originalName })
        assertEquals("DONE", repository.dao.findImportJob(jobId)?.status)
        assertEquals(0, File(context.filesDir, "import-staging").walkTopDown().count { it.isFile })
        println("EVIDENCE control normal_import=PASS")
    }

    @Test fun archiveJobIsRecordedBeforeTheSourceIsRead() = runBlocking {
        val jobId = repository.createArchiveImport(albumId, Uri.parse("content://missing/archive"))
        val job = repository.dao.findImportJob(jobId)!!
        assertEquals("PREPARING", job.status)
        assertTrue(job.folderUri.startsWith("archive:"))
        assertEquals(jobId, repository.dao.unfinishedImport(albumId)?.id)
        assertTrue(repository.pageSnapshot(albumId).isEmpty())
    }

    @Test fun pausedArchiveCanBeResumedAndImported() = runBlocking {
        val file = zip(listOf("1.png" to png(0xffff0000.toInt())))
        val jobId = repository.createArchiveImport(albumId, Uri.fromFile(file))
        repository.pauseImport(jobId)
        assertFalse(repository.prepareNextImport { _, _ -> })
        repository.resumeImport(jobId)
        assertTrue(repository.prepareNextImport { _, _ -> })
        assertTrue(repository.processNextImport {})
        assertEquals("DONE", repository.dao.findImportJob(jobId)?.status)
        assertEquals(1, repository.pageSnapshot(albumId).size)
    }

    @Test fun rejectedArchiveCanBeCancelledAndReplaced() = runBlocking {
        val file = zip(listOf("info.txt" to byteArrayOf(1)))
        val jobId = repository.createArchiveImport(albumId, Uri.fromFile(file))
        assertTrue(repository.prepareNextImport { _, _ -> })
        assertEquals("PAUSED", repository.dao.findImportJob(jobId)?.status)
        repository.cancelImport(jobId)
        assertEquals("CANCELLED", repository.dao.findImportJob(jobId)?.status)
        val replacement = zip(listOf("1.png" to png(0xffff0000.toInt())))
        repository.createArchiveImport(albumId, Uri.fromFile(replacement))
        assertTrue(repository.prepareNextImport { _, _ -> })
        assertTrue(repository.processNextImport {})
        assertEquals(1, repository.pageSnapshot(albumId).size)
    }

    private fun blockingSource(archive: Boolean): Pair<Uri, BlockingImportProvider> {
        val bytes = largeTestPng()
        val file = if (archive) zip(listOf("1.png" to bytes))
            else File(context.cacheDir, "source.png").apply { writeBytes(bytes) }
        val uri = Uri.parse("content://verification/source")
        val provider = BlockingImportProvider(file, if (archive) "application/zip" else "image/png")
        provider.register(context, uri)
        return uri to provider
    }

    @Test fun cancellingArchiveDuringExtractionDoesNotImportOrLeakFiles() = runBlocking {
        val (uri, provider) = blockingSource(archive = true)
        val jobId = repository.createArchiveImport(albumId, uri)
        val preparing = async(Dispatchers.IO) { repository.prepareNextImport { _, _ -> } }
        try {
            assertTrue(provider.blocked.await(10, TimeUnit.SECONDS))
            val cancelling = async(Dispatchers.IO) { repository.cancelImport(jobId) }
            withTimeout(5_000) {
                while (repository.dao.findImportJob(jobId)?.status != "CANCELLED") delay(10)
            }
            provider.release.countDown()
            preparing.await()
            cancelling.await()
            assertTrue(repository.pageSnapshot(albumId).isEmpty())
            assertEquals(0, File(context.filesDir, "import-staging").walkTopDown().count { it.isFile })
        } finally {
            provider.release.countDown()
            preparing.cancelAndJoin()
        }
    }

    @Test fun deletingDuringSelectedPreparationCompletesCleanupAfterCallerCancellation() = runBlocking {
        val (uri, provider) = blockingSource(archive = false)
        val jobId = repository.createSelectedImport(albumId, listOf(uri))
        val preparing = async(Dispatchers.IO) { repository.prepareNextImport { _, _ -> } }
        try {
            assertTrue(provider.blocked.await(10, TimeUnit.SECONDS))
            val deleting = async(Dispatchers.IO) { repository.deleteAlbum(albumId) }
            withTimeout(5_000) {
                while (repository.albumSnapshot(albumId) != null) delay(10)
            }
            deleting.cancel()
            provider.release.countDown()
            preparing.await()
            deleting.join()
            assertNull(repository.dao.findImportJob(jobId))
            assertEquals(0, File(context.filesDir, "import-staging").walkTopDown().count { it.isFile })
        } finally {
            provider.release.countDown()
            preparing.cancelAndJoin()
        }
    }

    @Test fun pausedExtractionRestartsAndOtherAlbumsCanQueueWhileItIsBlocked() = runBlocking {
        val (uri, provider) = blockingSource(archive = true)
        val jobId = repository.createArchiveImport(albumId, uri)
        val preparing = async(Dispatchers.IO) { repository.prepareNextImport { _, _ -> } }
        try {
            assertTrue(provider.blocked.await(10, TimeUnit.SECONDS))
            val otherAlbum = repository.createAlbum("另一图集")
            val photo = File(context.cacheDir, "other.png").apply { writeBytes(png(0xff00ff00.toInt())) }
            val otherJob = withTimeout(2_000) {
                repository.createSelectedImport(otherAlbum, listOf(Uri.fromFile(photo)))
            }
            assertEquals("PREPARING", repository.dao.findImportJob(otherJob)?.status)
            repository.pauseImport(jobId)
            provider.release.countDown()
            preparing.await()
            assertEquals("PAUSED", repository.dao.findImportJob(jobId)?.status)
            repository.resumeImport(jobId)
            assertTrue(repository.prepareNextImport { _, _ -> })
            assertTrue(repository.processNextImport {})
            assertEquals(1, repository.pageSnapshot(albumId).size)
            assertTrue(repository.prepareNextImport { _, _ -> })
            assertTrue(repository.processNextImport {})
            assertEquals(1, repository.pageSnapshot(otherAlbum).size)
        } finally {
            provider.release.countDown()
            preparing.cancelAndJoin()
        }
    }
}
