package com.project.lol.util

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

private const val URL_TAG = "MarkdownUrl"

private val UrlRegex = Regex("""https?://[\w\-./?%&=#:+~]+""")

@Immutable
internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<String>) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

private fun isFence(line: String): Boolean {
    var i = 0
    while (i < line.length && line[i].isWhitespace()) i++
    return line.startsWith("```", i)
}

private fun isRule(line: String): Boolean = line == "---" || line == "***" || line == "___"

private fun isBulletItem(line: String): Boolean =
    line.length >= 2 && line[1] == ' ' &&
            (line[0] == '-' || line[0] == '*' || line[0] == '+')

private fun numberedItemPrefixLength(line: String): Int {
    var i = 0
    while (i < line.length && line[i] in '0'..'9') i++
    if (i == 0 || i + 1 >= line.length || line[i] != '.') return -1
    val sep = line[i + 1]
    return if (sep == ' ' || sep == '\t') i + 2 else -1
}

private fun parseHeading(line: String): MarkdownBlock.Heading? {
    if (line.isEmpty() || line[0] != '#') return null
    var level = 0
    while (level < 6 && level < line.length && line[level] == '#') level++
    if (level >= line.length) return null
    val sep = line[level]
    if (sep != ' ' && sep != '\t') return null
    return MarkdownBlock.Heading(level, line.substring(level + 1).trim())
}

private fun isBlockStart(line: String, raw: String): Boolean =
    line.isEmpty() ||
            isFence(raw) ||
            parseHeading(line) != null ||
            line.startsWith("> ") ||
            isRule(line) ||
            isBulletItem(line) ||
            numberedItemPrefixLength(line) >= 0

internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()

        when {
            isFence(raw) -> {
                val code = StringBuilder()
                i++
                while (i < lines.size && !isFence(lines[i])) {
                    code.appendLine(lines[i])
                    i++
                }
                i++
                blocks += MarkdownBlock.CodeBlock(code.toString().trimEnd())
            }

            line.startsWith("> ") -> {
                blocks += MarkdownBlock.Quote(line.substring(2).trim())
                i++
            }

            isRule(line) -> {
                blocks += MarkdownBlock.Rule
                i++
            }

            else -> {
                val heading = parseHeading(line)
                when {
                    heading != null -> {
                        blocks += heading
                        i++
                    }

                    isBulletItem(line) -> {
                        val items = ArrayList<String>()
                        while (i < lines.size) {
                            val item = lines[i].trim()
                            if (!isBulletItem(item)) break
                            items += item.substring(2).trim()
                            i++
                        }
                        blocks += MarkdownBlock.BulletList(items)
                    }

                    numberedItemPrefixLength(line) >= 0 -> {
                        val items = ArrayList<String>()
                        while (i < lines.size) {
                            val item = lines[i].trim()
                            val prefix = numberedItemPrefixLength(item)
                            if (prefix < 0) break
                            items += item.substring(prefix).trim()
                            i++
                        }
                        blocks += MarkdownBlock.NumberedList(items)
                    }

                    line.isEmpty() -> i++
                    else -> {
                        val paragraph = StringBuilder(line)
                        i++
                        while (i < lines.size) {
                            val nextRaw = lines[i]
                            val next = nextRaw.trim()
                            if (isBlockStart(next, nextRaw)) break
                            paragraph.append(' ').append(next)
                            i++
                        }
                        blocks += MarkdownBlock.Paragraph(paragraph.toString())
                    }
                }
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
                    InlineMarkdownText(
                        text = block.text,
                        style = style.copy(fontWeight = FontWeight.Bold),
                        onLinkClick = onLinkClick
                    )
                }
                is MarkdownBlock.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    onLinkClick = onLinkClick
                )
                is MarkdownBlock.BulletList -> block.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        InlineMarkdownText(
                            text = item,
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
                        InlineMarkdownText(
                            text = item,
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
                            color = MaterialTheme.colorScheme.tertiary
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
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.tertiary

    val currentOnLinkClick by rememberUpdatedState(onLinkClick)

    val annotated = remember(text, linkColor, codeColor) {
        inlineAnnotated(text, linkColor, codeColor) { currentOnLinkClick(it) }
    }

    val color = if (style.color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        style.color
    }
    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier
    )
}

private fun inlineAnnotated(
    text: String,
    linkColor: Color,
    codeColor: Color,
    onLinkClick: (String) -> Unit
): AnnotatedString {
    val builder = AnnotatedString.Builder(text.length)
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)

    var i = 0
    var runStart = 0

    fun flushRun(end: Int) {
        if (end > runStart) builder.append(text, runStart, end)
    }

    while (i < text.length) {
        when (text[i]) {
            '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close > i + 1) {
                    flushRun(i)
                    val start = builder.length
                    builder.append(text, i + 1, close)
                    builder.addStyle(codeStyle, start, builder.length)
                    i = close + 1
                    runStart = i
                } else i++
            }

            '*' -> {
                if (i + 1 < text.length && text[i + 1] == '*') {
                    val close = text.indexOf("**", i + 2)
                    if (close > i + 2) {
                        flushRun(i)
                        val start = builder.length
                        builder.append(text, i + 2, close)
                        builder.addStyle(boldStyle, start, builder.length)
                        i = close + 2
                        runStart = i
                    } else i++
                } else {
                    val close = text.indexOf('*', i + 1)
                    if (close > i + 1) {
                        flushRun(i)
                        val start = builder.length
                        builder.append(text, i + 1, close)
                        builder.addStyle(italicStyle, start, builder.length)
                        i = close + 1
                        runStart = i
                    } else i++
                }
            }

            '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                val looksLikeLink = closeBracket > i + 1 &&
                        closeBracket + 1 < text.length &&
                        text[closeBracket + 1] == '('
                val closeParen =
                    if (looksLikeLink) text.indexOf(')', closeBracket + 2) else -1
                if (closeParen > closeBracket + 2) {
                    flushRun(i)
                    val url = text.substring(closeBracket + 2, closeParen)
                    builder.withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = linkStyles,
                            linkInteractionListener = { onLinkClick(url) }
                        )
                    ) {
                        append(text, i + 1, closeBracket)
                    }
                    i = closeParen + 1
                    runStart = i
                } else i++
            }

            'h' -> {
                val url = if (text.startsWith("http", i)) {
                    UrlRegex.matchAt(text, i)?.value
                } else null
                if (url != null) {
                    flushRun(i)
                    builder.withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = linkStyles,
                            linkInteractionListener = { onLinkClick(url) }
                        )
                    ) {
                        append(url)
                    }
                    i += url.length
                    runStart = i
                } else i++
            }

            else -> i++
        }
    }
    flushRun(text.length)
    return builder.toAnnotatedString()
}