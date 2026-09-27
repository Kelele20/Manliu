package com.kelele.manliu

internal fun readerResumeIndex(pages: List<ComicPage>, pageId: Long, fallbackIndex: Int): Int =
    if (pages.isEmpty()) 0 else pages.indexOfFirst { it.id == pageId }.takeIf { it >= 0 }
        ?: fallbackIndex.coerceIn(0, pages.lastIndex)

internal fun readerPageIdAt(pages: List<ComicPage>, index: Int): Long =
    pages.getOrNull(index)?.id ?: 0L
