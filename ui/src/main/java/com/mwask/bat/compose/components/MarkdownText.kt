package com.mwask.bat.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme

private sealed class Block {
    data class Heading(val level: Int, val text: AnnotatedString) : Block()
    data class Paragraph(val text: AnnotatedString, val hasLinks: Boolean) : Block()
    data class ListItem(val marker: String, val text: AnnotatedString) : Block()
    data class Quote(val text: AnnotatedString) : Block()
    data class Code(val lines: List<String>) : Block()
    data object Rule : Block()
}

private val INLINE = Regex(
    """(\*\*(.+?)\*\*)|(__(.+?)__)|(\*(.+?)\*)|(`([^`]+)`)|(~~(.+?)~~)|(\[([^\]]+)]\(([^)\s]+)\))""",
    RegexOption.DOT_MATCHES_ALL
)

private const val TAG_URL = "MARKDOWN_URL"

private fun parseInline(text: String): Pair<AnnotatedString, Boolean> {
    var hasLinks = false
    val builder = buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val match = INLINE.find(text, index) ?: break
            if (match.range.first > index) append(text.substring(index, match.range.first))
            val groups = match.groupValues
            var pushed = 1
            when {
                groups[1].isNotEmpty() -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                groups[3].isNotEmpty() -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                groups[5].isNotEmpty() -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                groups[7].isNotEmpty() -> pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.White.copy(alpha = 0.10f),
                        fontSize = 13.sp
                    )
                )
                groups[9].isNotEmpty() -> pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                groups[11].isNotEmpty() -> {
                    hasLinks = true
                    pushStringAnnotation(TAG_URL, groups[13])
                    pushStyle(
                        SpanStyle(
                            color = EncoreColors.Green,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    pushed = 2
                }
                else -> pushed = 0
            }
            append(
                groups[2].ifEmpty {
                    groups[4].ifEmpty {
                        groups[6].ifEmpty {
                            groups[8].ifEmpty {
                                groups[10].ifEmpty { groups[12] }
                            }
                        }
                    }
                }
            )
            repeat(pushed) { pop() }
            index = match.range.last + 1
        }
        if (index < text.length) append(text.substring(index))
    }
    return builder to hasLinks
}

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = markdown.replace("\r\n", "\n").split("\n")
    var paragraph = mutableListOf<String>()
    var quote = mutableListOf<String>()
    var fence: MutableList<String>? = null

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val (text, hasLinks) = parseInline(paragraph.joinToString(" ").trim())
        if (text.isNotEmpty()) blocks.add(Block.Paragraph(text, hasLinks))
        paragraph = mutableListOf()
    }

    fun flushQuote() {
        if (quote.isEmpty()) return
        val (text, _) = parseInline(quote.joinToString(" ").trim())
        if (text.isNotEmpty()) blocks.add(Block.Quote(text))
        quote = mutableListOf()
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        val trimmed = line.trim()
        val fenceMark = Regex("^```").find(trimmed) != null || Regex("^~~~").find(trimmed) != null
        if (fenceMark || fence != null) {
            if (fence == null) {
                flushParagraph()
                flushQuote()
                fence = mutableListOf()
            } else if (fenceMark) {
                if (fence.isNotEmpty()) blocks.add(Block.Code(fence.toList()))
                fence = null
            } else {
                fence.add(raw)
            }
            continue
        }
        if (trimmed.isEmpty()) {
            flushParagraph()
            flushQuote()
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (heading != null) {
            flushParagraph()
            flushQuote()
            val (text, _) = parseInline(heading.groupValues[2])
            blocks.add(Block.Heading(heading.groupValues[1].length, text))
            continue
        }
        if (Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(trimmed)) {
            flushParagraph()
            flushQuote()
            blocks.add(Block.Rule)
            continue
        }
        if (trimmed.startsWith(">")) {
            flushParagraph()
            quote.add(trimmed.removePrefix(">").trim())
            continue
        }
        flushQuote()
        val bullet = Regex("^[-*+]\\s+(.*)$").find(trimmed)
        if (bullet != null) {
            flushParagraph()
            val (text, _) = parseInline(bullet.groupValues[1])
            blocks.add(Block.ListItem("\u2022", text))
            continue
        }
        val ordered = Regex("^(\\d{1,3})[.)]\\s+(.*)$").find(trimmed)
        if (ordered != null) {
            flushParagraph()
            val (text, _) = parseInline(ordered.groupValues[2])
            blocks.add(Block.ListItem("${ordered.groupValues[1]}.", text))
            continue
        }
        paragraph.add(trimmed)
    }
    if (fence != null && fence.isNotEmpty()) blocks.add(Block.Code(fence.toList()))
    flushParagraph()
    flushQuote()
    return blocks
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> EncoreTheme.typography.titleLarge
                        2 -> EncoreTheme.typography.titleMedium
                        else -> EncoreTheme.typography.titleSmall
                    }.copy(fontWeight = FontWeight.Bold, color = EncoreColors.TextPrimary)
                )
                is Block.Paragraph -> if (block.hasLinks) {
                    ClickableText(
                        text = block.text,
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextPrimary),
                        onClick = { offset ->
                            block.text.getStringAnnotations(TAG_URL, offset, offset).firstOrNull()?.let { url ->
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url.item))
                                    )
                                }
                            }
                        }
                    )
                } else {
                    Text(
                        text = block.text,
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextPrimary)
                    )
                }
                is Block.ListItem -> Row {
                    Text(
                        text = block.marker,
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = block.text,
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextPrimary)
                    )
                }
                is Block.Quote -> Row {
                    Column(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .background(EncoreColors.Green, RoundedCornerShape(2.dp))
                    ) {}
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = block.text,
                        style = EncoreTheme.typography.bodyMedium.copy(
                            color = EncoreColors.TextSecondary,
                            fontStyle = FontStyle.Italic
                        )
                    )
                }
                is Block.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    block.lines.forEach {
                        Text(
                            text = it,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = EncoreColors.TextPrimary
                            )
                        )
                    }
                }
                Block.Rule -> HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
            }
        }
    }
}
