package com.kelele.manliu

import java.io.IOException

internal object ArchiveImportLimits {
    const val MAX_IMAGES = 10_000
    const val MAX_IMAGE_BYTES = ArchiveLimits.MAX_IMAGE_BYTES
    const val STORAGE_RESERVE_BYTES = 64L * 1024 * 1024
}

private val archiveImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

/** Keep the relative path so page numbering can restart within each chapter. */
internal fun archiveImagePath(name: String, isDirectory: Boolean): String? {
    if (isDirectory) return null
    val path = name.replace('\\', '/').trimStart('/')
    val parts = path.split('/')
    if (parts.any { it.isEmpty() || it.startsWith('.') || it == "__MACOSX" }) return null
    val extension = parts.last().substringAfterLast('.', "").lowercase()
    return path.takeIf { extension in archiveImageExtensions }
}

/** Validate expanded bytes before writing, including entries filtered out of the import. */
internal class ArchiveExtractionBudget(private val maxTotalBytes: Long) {
    private var expandedBytes = 0L

    fun checkImageCount(count: Int) {
        if (count > ArchiveImportLimits.MAX_IMAGES) throw IOException("一次最多导入 10000 张")
    }

    fun consume(entryBytes: Long, count: Int, availableBytes: Long) {
        if (entryBytes > ArchiveImportLimits.MAX_IMAGE_BYTES) throw IOException("压缩包中单个文件超过 100 MiB")
        if (count.toLong() > maxTotalBytes - expandedBytes ||
            availableBytes - count < ArchiveImportLimits.STORAGE_RESERVE_BYTES
        ) throw IOException("存储空间不足，请清理空间后继续")
        expandedBytes += count
    }
}
