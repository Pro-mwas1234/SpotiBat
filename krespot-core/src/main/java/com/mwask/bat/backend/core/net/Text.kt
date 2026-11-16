/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (SpotiBat). All Rights Reserved.
 * Original repository: https://github.com/Pro-mwas1234/SpotiBat
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.core.net

private val TAG_RE = Regex("<[^>]*>")
private val ENTITY_RE = Regex("&(#[xX]?[0-9a-fA-F]{1,8}|[a-zA-Z][a-zA-Z0-9]{1,31});")
private val SPACE_RE = Regex("[\\s\\u00A0]+")
private val TIGHT_LEFT_RE = Regex("\\s+([,.;:!?)\\]}»])")
private val TIGHT_RIGHT_RE = Regex("([(\\[{«])\\s+")

fun String.plainText(): String {
    if (isEmpty()) return this
    if (this == "null" || this == "undefined") return ""
    if (indexOf('<') < 0 && indexOf('&') < 0) return this
    val stripped = TAG_RE.replace(this, " ")
    val decoded = ENTITY_RE.replace(stripped) { match -> entity(match.groupValues[1]) ?: match.value }
    val spaced = SPACE_RE.replace(decoded, " ")
    return TIGHT_LEFT_RE.replace(TIGHT_RIGHT_RE.replace(spaced, "$1"), "$1").trim()
}

private fun entity(body: String): String? = when {
    body.startsWith("#") -> codePoint(body)
    else -> when (body.lowercase()) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        "nbsp" -> " "
        "ndash" -> "\u2013"
        "mdash" -> "\u2014"
        "hellip" -> "\u2026"
        "lsquo", "rsquo" -> "\u2019"
        "ldquo", "rdquo" -> "\u201D"
        "middot" -> "\u00B7"
        "bull" -> "\u2022"
        "copy" -> "\u00A9"
        "reg" -> "\u00AE"
        "trade" -> "\u2122"
        "deg" -> "\u00B0"
        "times" -> "\u00D7"
        else -> null
    }
}

private fun codePoint(body: String): String? {
    val digits = body.removePrefix("#")
    val code = when {
        digits.startsWith("x") || digits.startsWith("X") -> digits.substring(1).toIntOrNull(16)
        else -> digits.toIntOrNull()
    } ?: return null
    if (code <= 0 || code > 0x10FFFF) return null
    if (code in 0xD800..0xDFFF) return null
    return String(Character.toChars(code))
}
