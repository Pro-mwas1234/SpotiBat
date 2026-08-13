package com.project.lol.util

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private const val URL_TAG = "MarkdownUrl"

private val HeadingRegex = Regex("^(#{1,6})\\s+(.*)")
private val NumberedRegex = Regex("^\\d+\\.\\s+")
private val UrlRegex = Regex("""https?://[\w\-./?%&=#:+~]+""")

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<String>) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0

    fun isFence(line: String) = line.trimStart().startsWith("```")

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            isFence(line) -> {
                val sb = StringBuilder()
                i++
                while (i < lines.size && !isFence(lines[i])) {
                    sb.appendLine(lines[i])
                    i++
                }
                i++
                blocks += MarkdownBlock.CodeBlock(sb.toString().trimEnd())
            }
            trimmed.startsWith("> ") -> {
                blocks += MarkdownBlock.Quote(trimmed.drop(2).trim())
                i++
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks += MarkdownBlock.Rule
                i++
            }
            HeadingRegex.matches(trimmed) -> {
                val m = HeadingRegex.find(trimmed)!!
                blocks += MarkdownBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                val items = mutableListOf(trimmed.drop(2).trim())
                i++
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) {
                        items += t.drop(2).trim()
                        i++
                    } else break
                }
                blocks += MarkdownBlock.BulletList(items)
            }
            NumberedRegex.containsMatchIn(trimmed) -> {
                val items = mutableListOf(NumberedRegex.replace(trimmed, "").trim())
                i++
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (NumberedRegex.containsMatchIn(t)) {
                        items += NumberedRegex.replace(t, "").trim()
                        i++
                    } else break
                }
                blocks += MarkdownBlock.NumberedList(items)
            }
            trimmed.isEmpty() -> i++
            else -> {
                val sb = StringBuilder(trimmed)
                i++
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.isEmpty() || isFence(lines[i]) || HeadingRegex.matches(t) ||
                        t.startsWith("> ") || t == "---" || t == "***" || t == "___" ||
                        t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") ||
                        NumberedRegex.containsMatchIn(t)
                    ) break
                    sb.append(' ').append(t)
                    i++
                }
                blocks += MarkdownBlock.Paragraph(sb.toString())
            }
        }
    }
    return blocks
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        3 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    InlineText(
                        text = inlineAnnotated(block.text, linkColor, codeColor),
                        style = style.copy(fontWeight = FontWeight.Bold),
                        onLinkClick = onLinkClick
                    )
                }
                is MarkdownBlock.Paragraph -> InlineText(
                    text = inlineAnnotated(block.text, linkColor, codeColor),
                    style = MaterialTheme.typography.bodyMedium,
                    onLinkClick = onLinkClick
                )
                is MarkdownBlock.BulletList -> block.items.forEachIndexed { _, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        InlineText(
                            text = inlineAnnotated(item, linkColor, codeColor),
                            style = MaterialTheme.typography.bodyMedium,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.NumberedList -> block.items.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        InlineText(
                            text = inlineAnnotated(item, linkColor, codeColor),
                            style = MaterialTheme.typography.bodyMedium,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor
                        ),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                is MarkdownBlock.Quote -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                MarkdownBlock.Rule -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }
        }
    }
}

@Composable
private fun InlineText(
    text: AnnotatedString,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = if (style.color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        style.color
    }
    ClickableText(
        text = text,
        style = style.copy(color = baseColor),
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}

private fun inlineAnnotated(text: String, linkColor: Color, codeColor: Color): AnnotatedString {
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                val close = text.indexOf("**", i + 2)
                if (close > i + 2) {
                    val start = b.length
                    b.append(text.substring(i + 2, close))
                    b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, b.length)
                    i = close + 2
                } else {
                    b.append(c)
                    i++
                }
            }
            c == '*' -> {
                val close = text.indexOf('*', i + 1)
                if (close > i + 1) {
                    val start = b.length
                    b.append(text.substring(i + 1, close))
                    b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, b.length)
                    i = close + 1
                } else {
                    b.append(c)
                    i++
                }
            }
            c == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close > i + 1) {
                    val start = b.length
                    b.append(text.substring(i + 1, close))
                    b.addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor),
                        start, b.length
                    )
                    i = close + 1
                } else {
                    b.append(c)
                    i++
                }
            }
            c == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i + 1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        val start = b.length
                        b.append(label)
                        b.addStyle(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            start, b.length
                        )
                        b.addStringAnnotation(URL_TAG, url, start, b.length)
                        i = closeParen + 1
                    } else {
                        b.append(c)
                        i++
                    }
                } else {
                    b.append(c)
                    i++
                }
            }
            c == 'h' -> {
                val urlMatch = UrlRegex.find(text, i)
                if (urlMatch != null && urlMatch.range.first == i) {
                    val url = urlMatch.value
                    val start = b.length
                    b.append(url)
                    b.addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        start, b.length
                    )
                    b.addStringAnnotation(URL_TAG, url, start, b.length)
                    i += url.length
                } else {
                    b.append(c)
                    i++
                }
            }
            else -> {
                b.append(c)
                i++
            }
        }
    }
    return b.toAnnotatedString()
}
