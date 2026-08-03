package dev.pschmitt.nyetbox.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Renders a cached-list value with every case-insensitive query match emphasized, similar to
 * grep's `--color=auto` output. The full value remains the accessibility text; the spans are only
 * a visual aid.
 */
@Composable
fun SearchHighlightedText(
    value: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text =
            highlightedSearchText(
                value,
                query,
                SpanStyle(
                    background = colors.secondaryContainer,
                    color = colors.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                ),
            ),
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

internal fun highlightedSearchText(
    value: String,
    query: String,
    matchStyle: SpanStyle,
): AnnotatedString {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (value.isEmpty() || terms.isEmpty()) return AnnotatedString(value)

    val ranges =
        terms
            .flatMap { term ->
                buildList {
                    var start = 0
                    while (start <= value.length - term.length) {
                        val index =
                            value.indexOf(term, startIndex = start, ignoreCase = true)
                        if (index < 0) break
                        add(index until index + term.length)
                        start = index + term.length.coerceAtLeast(1)
                    }
                }
            }
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { merged, range ->
                val previous = merged.lastOrNull()
                if (previous != null && range.first <= previous.last + 1) {
                    merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
                } else {
                    merged += range
                }
                merged
            }

    if (ranges.isEmpty()) return AnnotatedString(value)
    return AnnotatedString.Builder().apply {
        append(value)
        ranges.forEach { range -> addStyle(matchStyle, range.first, range.last + 1) }
    }.toAnnotatedString()
}
