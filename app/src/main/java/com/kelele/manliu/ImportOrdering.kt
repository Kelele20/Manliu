package com.kelele.manliu

/** Place a retried item beside its nearest surviving sibling without reordering existing pages. */
internal fun importInsertionPosition(
    nextSequencePosition: Int?,
    previousSequencePosition: Int?,
    appendPosition: Int,
): Int = nextSequencePosition ?: previousSequencePosition?.plus(1) ?: appendPosition
