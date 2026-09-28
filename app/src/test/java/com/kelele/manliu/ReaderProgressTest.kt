package com.kelele.manliu

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressTest {
    private fun page(id: Long) = ComicPage(
        id = id, albumId = 1, position = id.toInt(), fileName = "$id.jpg",
        originalName = "$id.jpg", width = 1, height = 1,
    )

    @Test fun savedPageIdSurvivesInsertionBeforeIt() {
        val before = listOf(page(1), page(3))
        val savedId = readerPageIdAt(before, 1)
        val after = listOf(page(1), page(2), page(3))
        assertEquals(3L, savedId)
        assertEquals(2, readerResumeIndex(after, savedId, fallbackIndex = 1))
    }

    @Test fun progressReadsLatestPageList() {
        val updated = listOf(page(1), page(2), page(3))
        assertEquals(2L, readerPageIdAt(updated, 1))
        assertEquals(0L, readerPageIdAt(updated, 5))
    }
}
