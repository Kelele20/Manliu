package com.kelele.manliu

/** Compare names as people read page numbers: 2.jpg comes before 10.jpg. */
internal fun compareImageNames(first: String, second: String): Int {
    var left = 0
    var right = 0
    while (left < first.length && right < second.length) {
        if (first[left].isDigit() && second[right].isDigit()) {
            val leftStart = left
            val rightStart = right
            while (left < first.length && first[left].isDigit()) left++
            while (right < second.length && second[right].isDigit()) right++
            var leftValue = leftStart
            var rightValue = rightStart
            while (leftValue < left && first[leftValue] == '0') leftValue++
            while (rightValue < right && second[rightValue] == '0') rightValue++
            val lengthDifference = (left - leftValue).compareTo(right - rightValue)
            if (lengthDifference != 0) return lengthDifference
            while (leftValue < left) {
                val difference = first[leftValue].compareTo(second[rightValue])
                if (difference != 0) return difference
                leftValue++
                rightValue++
            }
        } else {
            val difference = first[left].lowercaseChar().compareTo(second[right].lowercaseChar())
            if (difference != 0) return difference
            left++
            right++
        }
    }
    val lengthDifference = (first.length - left).compareTo(second.length - right)
    return if (lengthDifference != 0) lengthDifference else first.compareTo(second)
}
