package com.kelele.manliu

import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveExtractionBudgetTest {
    @Test fun imageCountAccepts10000AndRejects10001() {
        val budget = ArchiveExtractionBudget(Long.MAX_VALUE)
        budget.checkImageCount(10_000)
        assertThrows(IOException::class.java) { budget.checkImageCount(10_001) }
    }

    @Test fun imageBytesAccept100MiBAndRejectTheNextByte() {
        val budget = ArchiveExtractionBudget(Long.MAX_VALUE)
        budget.consume(ArchiveImportLimits.MAX_IMAGE_BYTES, 1, Long.MAX_VALUE)
        assertThrows(IOException::class.java) {
            budget.consume(ArchiveImportLimits.MAX_IMAGE_BYTES + 1, 1, Long.MAX_VALUE)
        }
    }

    @Test fun expandedBytesAreCountedAcrossEntries() {
        val budget = ArchiveExtractionBudget(10)
        budget.consume(6, 6, Long.MAX_VALUE)
        budget.consume(4, 4, Long.MAX_VALUE)
        assertThrows(IOException::class.java) { budget.consume(1, 1, Long.MAX_VALUE) }
    }

    @Test fun writingCannotUseTheStorageReserve() {
        val budget = ArchiveExtractionBudget(Long.MAX_VALUE)
        val reserve = ArchiveImportLimits.STORAGE_RESERVE_BYTES
        budget.consume(1, 1, reserve + 1)
        assertThrows(IOException::class.java) { budget.consume(1, 1, reserve) }
    }
}
