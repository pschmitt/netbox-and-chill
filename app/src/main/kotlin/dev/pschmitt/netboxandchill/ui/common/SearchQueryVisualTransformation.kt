package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.pschmitt.netboxandchill.data.repository.parseGlobalSearchQuery

/** Highlights recognized `field:value`/`field=value` tokens without changing their text offsets. */
class SearchQueryVisualTransformation(private val accent: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val query = parseGlobalSearchQuery(text.text)
        if (query.filters.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val styled = AnnotatedString.Builder(text)
        query.filters.forEach { filter ->
            styled.addStyle(
                SpanStyle(background = accent.copy(alpha = 0.14f)),
                filter.tokenRange.first,
                filter.tokenRange.last + 1,
            )
            styled.addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                filter.keyRange.first,
                filter.keyRange.last + 1,
            )
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}
