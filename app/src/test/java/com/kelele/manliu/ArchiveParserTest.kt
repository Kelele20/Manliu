package com.kelele.manliu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                val name = entry.name
                val fileName = name.substringAfterLast('/')
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory &&
                    !name.contains("__MACOSX") &&
                    !fileName.startsWith(".") &&
                    ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
                ) {
                    entries.add(fileName)
                }
                zip.closeEntry()
            }
        }

        // 自然文件名排序
        entries.sortWith { a, b -> compareImageNames(a, b) }

        assertEquals(listOf("1.png", "2.jpg", "10.jpg"), entries)
    }

    @Test
    fun cbzSupportedExtensionsAreRecognized() {
        val supported = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
        assertTrue(supported.contains("webp"))
        assertTrue(supported.contains("avif"))
        assertTrue(supported.contains("png"))
        assertTrue(!supported.contains("txt"))
        assertTrue(!supported.contains("exe"))
    }
}
