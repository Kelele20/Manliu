package com.kelele.manliu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveLimitsTest {
    @Test
    fun albumLimitAccepts1000AndRejects1001BeforeOpeningOutput() {
        assertTrue(outputOpensFor(albums = 1_000, images = 0))
        assertFalse(outputOpensFor(albums = 1_001, images = 0))
    }

    @Test
    fun imageLimitAccepts200000AndRejects200001BeforeOpeningOutput() {
        assertTrue(outputOpensFor(albums = 1, images = 200_000))
        assertFalse(outputOpensFor(albums = 1, images = 200_001))
    }

    @Test
    fun manifestAndImageByteLimitsMatchRestoreLimits() {
        ArchiveLimits.validateManifestSize(ArchiveLimits.MAX_MANIFEST_BYTES)
        ArchiveLimits.validateImageSize(ArchiveLimits.MAX_IMAGE_BYTES, "page.jpg")

        assertThrows(java.io.IOException::class.java) {
            ArchiveLimits.validateManifestSize(ArchiveLimits.MAX_MANIFEST_BYTES + 1)
        }
        assertThrows(java.io.IOException::class.java) {
            ArchiveLimits.validateImageSize(ArchiveLimits.MAX_IMAGE_BYTES + 1, "page.jpg")
        }
        assertThrows(java.io.IOException::class.java) {
            ArchiveLimits.validateImageSize(0, "empty.jpg")
        }
    }

    @Test
    fun openAfterValidationDoesNotOpenOutputForOversizedManifestOrImage() {
        var opened = false
        assertThrows(java.io.IOException::class.java) {
            ArchiveLimits.openAfterValidation(
                albumCount = 1,
                imageCount = 1,
                manifestBytes = ArchiveLimits.MAX_MANIFEST_BYTES + 1,
                imageSizes = emptyList(),
            ) { opened = true }
        }
        assertFalse(opened)

        assertThrows(java.io.IOException::class.java) {
            ArchiveLimits.openAfterValidation(
                albumCount = 1,
                imageCount = 1,
                manifestBytes = 1,
                imageSizes = listOf("large.jpg" to ArchiveLimits.MAX_IMAGE_BYTES + 1),
            ) { opened = true }
        }
        assertFalse(opened)
    }

    @Test
    fun validArchiveOpensOutputAfterValidation() {
        val result = ArchiveLimits.openAfterValidation(
            albumCount = 1_000,
            imageCount = 200_000,
            manifestBytes = ArchiveLimits.MAX_MANIFEST_BYTES,
            imageSizes = listOf("page.jpg" to ArchiveLimits.MAX_IMAGE_BYTES),
        ) { "opened" }

        assertEquals("opened", result)
    }

    private fun outputOpensFor(albums: Int, images: Long): Boolean {
        var opened = false
        try {
            ArchiveLimits.openAfterValidation(
                albumCount = albums,
                imageCount = images,
                manifestBytes = 0,
                imageSizes = emptyList(),
            ) { opened = true }
        } catch (_: java.io.IOException) {
            // Rejected archives must leave the target output untouched.
        }
        return opened
    }
}
