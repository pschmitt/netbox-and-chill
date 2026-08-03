package dev.pschmitt.nyetbox.ui.dashboard

/** One token in a before/after text diff. */
internal data class InlineDiffToken(
    val text: String,
    val kind: InlineDiffTokenKind,
)

internal enum class InlineDiffTokenKind {
    UNCHANGED,
    REMOVED,
    ADDED,
}

internal data class InlineDiff(
    val before: List<InlineDiffToken>,
    val after: List<InlineDiffToken>,
)

private const val MAX_DIFF_TOKENS = 400

/**
 * Builds a compact word/punctuation-level diff while retaining whitespace in the rendered text.
 * The bounded input keeps large JSON or Markdown fields from turning the quadratic LCS pass into
 * an expensive UI operation; those fields still render correctly, just as a single changed span.
 */
internal fun buildInlineDiff(before: String?, after: String?): InlineDiff {
    val beforeTokens = tokenizeForDiff(before.orEmpty())
    val afterTokens = tokenizeForDiff(after.orEmpty())
    if (beforeTokens.size > MAX_DIFF_TOKENS || afterTokens.size > MAX_DIFF_TOKENS) {
        return InlineDiff(
            before = beforeTokens.map { InlineDiffToken(it, InlineDiffTokenKind.REMOVED) },
            after = afterTokens.map { InlineDiffToken(it, InlineDiffTokenKind.ADDED) },
        )
    }

    val commonLengths = Array(beforeTokens.size + 1) { IntArray(afterTokens.size + 1) }
    for (beforeIndex in beforeTokens.indices.reversed()) {
        for (afterIndex in afterTokens.indices.reversed()) {
            commonLengths[beforeIndex][afterIndex] =
                if (beforeTokens[beforeIndex] == afterTokens[afterIndex]) {
                    commonLengths[beforeIndex + 1][afterIndex + 1] + 1
                } else {
                    maxOf(
                        commonLengths[beforeIndex + 1][afterIndex],
                        commonLengths[beforeIndex][afterIndex + 1],
                    )
                }
        }
    }

    val beforeDiff = mutableListOf<InlineDiffToken>()
    val afterDiff = mutableListOf<InlineDiffToken>()
    var beforeIndex = 0
    var afterIndex = 0
    while (beforeIndex < beforeTokens.size || afterIndex < afterTokens.size) {
        when {
            beforeIndex == beforeTokens.size -> {
                afterDiff += InlineDiffToken(afterTokens[afterIndex++], InlineDiffTokenKind.ADDED)
            }
            afterIndex == afterTokens.size -> {
                beforeDiff +=
                    InlineDiffToken(beforeTokens[beforeIndex++], InlineDiffTokenKind.REMOVED)
            }
            beforeTokens[beforeIndex] == afterTokens[afterIndex] -> {
                val token = beforeTokens[beforeIndex++]
                afterIndex++
                beforeDiff += InlineDiffToken(token, InlineDiffTokenKind.UNCHANGED)
                afterDiff += InlineDiffToken(token, InlineDiffTokenKind.UNCHANGED)
            }
            commonLengths[beforeIndex + 1][afterIndex] >=
                commonLengths[beforeIndex][afterIndex + 1] -> {
                beforeDiff +=
                    InlineDiffToken(beforeTokens[beforeIndex++], InlineDiffTokenKind.REMOVED)
            }
            else -> {
                afterDiff += InlineDiffToken(afterTokens[afterIndex++], InlineDiffTokenKind.ADDED)
            }
        }
    }
    return InlineDiff(beforeDiff, afterDiff)
}

private fun tokenizeForDiff(value: String): List<String> =
    Regex("\\s+|[\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_\\s]")
        .findAll(value)
        .map(MatchResult::value)
        .toList()
