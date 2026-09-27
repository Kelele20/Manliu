package com.kelele.manliu

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportOrderingTest {
    @Test fun retryFillsGapWithoutChangingExistingOrder() {
        val pages = mutableListOf("1", "3")
        val position = importInsertionPosition(nextSequencePosition = 1, previousSequencePosition = 0, appendPosition = 2)
        pages.add(position, "2")
        assertEquals(listOf("1", "2", "3"), pages)
    }

    @Test fun pauseThenResumeAppendsNextItem() {
        assertEquals(2, importInsertionPosition(null, previousSequencePosition = 1, appendPosition = 2))
    }

    @Test fun newTaskKeepsEarlierAlbumPagesInPlace() {
        assertEquals(4, importInsertionPosition(null, null, appendPosition = 4))
    }

    @Test fun retryUsesSuccessorAfterUserReordersAlbum() {
        val pages = mutableListOf("unrelated", "3", "1")
        val position = importInsertionPosition(nextSequencePosition = 1, previousSequencePosition = 2, appendPosition = 3)
        pages.add(position, "2")
        assertEquals(listOf("unrelated", "2", "3", "1"), pages)
    }
}
