package com.kelele.manliu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveParserTest {
    @Test
    fun zipArchiveFiltersNonImagesAndOrdersNaturally() {
        // 创建一个包含乱序图片、隐藏文件和非图片文件的 ZIP 包
        val byteOutput = ByteArrayOutputStream()
        ZipOutputStream(byteOutput).use { zip ->
            zip.putNextEntry(ZipEntry("manga/__MACOSX/._p1.jpg"))
            zip.write("mac metadata".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manga/.DS_Store"))
            zip.write("store".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manga/info.txt"))
            zip.write("read me".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manga/10.jpg"))
            zip.write("image 10".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manga/2.jpg"))
            zip.write("image 2".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manga/1.png"))
            zip.write("image 1".toByteArray())
            zip.closeEntry()
        }

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(byteOutput.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                archiveImagePath(entry.name, entry.isDirectory)?.let(entries::add)
                zip.closeEntry()
            }
        }

        // 自然文件名排序
        entries.sortWith { a, b -> compareImageNames(a, b) }

        assertEquals(listOf("manga/1.png", "manga/2.jpg", "manga/10.jpg"), entries)
    }

    @Test
    fun cbzSupportedExtensionsAreRecognized() {
        for (extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")) {
            assertEquals("1.$extension", archiveImagePath("1.$extension", false))
        }
        assertNull(archiveImagePath("info.txt", false))
        assertNull(archiveImagePath("program.exe", false))
    }

    @Test fun hiddenDirectoriesAndTraversalAreFiltered() {
        assertNull(archiveImagePath(".hidden/1.jpg", false))
        assertNull(archiveImagePath("../1.jpg", false))
        assertNull(archiveImagePath("chapter/../../1.jpg", false))
        assertNull(archiveImagePath("chapter.jpg", true))
        assertEquals("chapter/1.jpg", archiveImagePath("chapter\\1.jpg", false))
    }

    @Test fun relativePathsKeepNaturallyNumberedChaptersTogether() {
        val paths = listOf("chapter10/1.jpg", "chapter2/2.jpg", "chapter1/10.jpg", "chapter2/1.jpg", "chapter1/2.jpg")
            .sortedWith(::compareImageNames)
        assertEquals(listOf("chapter1/2.jpg", "chapter1/10.jpg", "chapter2/1.jpg", "chapter2/2.jpg", "chapter10/1.jpg"), paths)
    }
}
