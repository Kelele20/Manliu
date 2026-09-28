package com.kelele.manliu

import java.io.IOException

/** Limits shared by backup creation and restore so every exported archive is restorable. */
internal object ArchiveLimits {
    const val MAX_ALBUMS = 1_000
    const val MAX_IMAGES = 200_000L
    const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024
    const val MAX_IMAGE_BYTES = 100L * 1024 * 1024

    fun validateAlbumCount(count: Int) {
        if (count !in 1..MAX_ALBUMS) throw IOException("备份图集数量必须为 1 到 $MAX_ALBUMS 个")
    }

    fun validateImageCount(count: Long) {
        if (count !in 0..MAX_IMAGES) throw IOException("备份图片数量不能超过 $MAX_IMAGES 张")
    }

    fun validateManifestSize(size: Long) {
        if (size > MAX_MANIFEST_BYTES) throw IOException("备份目录超过 64 MiB，无法恢复")
    }

    fun validateImageSize(size: Long, name: String) {
        if (size <= 0) throw IOException("图片“$name”为空，无法恢复")
        if (size > MAX_IMAGE_BYTES) throw IOException("图片“$name”超过 100 MiB，无法恢复")
    }

    /** Invoke the destination opener only after all export/restore symmetry checks pass. */
    fun <T> openAfterValidation(
        albumCount: Int,
        imageCount: Long,
        manifestBytes: Long,
        imageSizes: Iterable<Pair<String, Long>>,
        openOutput: () -> T,
    ): T {
        validateAlbumCount(albumCount)
        validateImageCount(imageCount)
        validateManifestSize(manifestBytes)
        imageSizes.forEach { (name, size) -> validateImageSize(size, name) }
        return openOutput()
    }
}
