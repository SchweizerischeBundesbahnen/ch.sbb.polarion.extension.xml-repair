package ch.sbb.polarion.extension.xml_repair.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("java:S125") // suppress false-positive commented-out lines of code
class HtmlUtilsTest {

    // === Plain input / no normalization needed ===

    @Test
    void testPlainTextUnchanged() {
        // No entity matches (loop body never entered) and no special whitespace -> string returned as-is.
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello world"));
    }

    @Test
    void testEmptyString() {
        // matcher.find() is false -> only appendTail runs; literal pass is a no-op.
        assertEquals("", HtmlUtils.cleanupHtmlSpaces(""));
    }

    // === Literal whitespace / zero-width characters (WHITESPACE_CLASS pass) ===

    @Test
    void testLiteralNonBreakingSpace() {
        // U+00A0 is in \p{Zs}.
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello world"));
    }

    @Test
    void testLiteralThinSpace() {
        // U+2009 is in \p{Zs}.
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello world"));
    }

    @Test
    void testLiteralZeroWidthSpace() {
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello​world"));
    }

    @Test
    void testLiteralZeroWidthNonJoiner() {
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello‌world"));
    }

    @Test
    void testLiteralZeroWidthJoiner() {
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello‍world"));
    }

    @Test
    void testLiteralWordJoiner() {
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello⁠world"));
    }

    @Test
    void testLiteralBom() {
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello﻿world"));
    }

    // === Named &nbsp; entity ("&nbsp;".equalsIgnoreCase branch) ===

    @Test
    void testNamedNbspEntity() {
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello&nbsp;world"));
    }

    @Test
    void testNamedNbspEntityCaseInsensitive() {
        // CASE_INSENSITIVE pattern + equalsIgnoreCase both accept upper case.
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello&NBSP;world"));
    }

    // === Numeric references that decode to whitespace (decodesToWhitespace == true) ===

    @Test
    void testNumericDecimalNbsp() {
        // decimal branch (no leading x), code point 160 (U+00A0) -> whitespace.
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello&#160;world"));
    }

    @Test
    void testNumericHexNbspLowerCaseX() {
        // hex branch via body.startsWith("x"), code point 0xA0 (U+00A0).
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello&#xA0;world"));
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello&#xa0;world"));
    }

    @Test
    void testNumericHexNbspUpperCaseX() {
        // hex branch via body.startsWith("X") (matched because the pattern is CASE_INSENSITIVE);
        // code point 0x20 (U+0020 SPACE) is in \p{Zs}.
        assertEquals("a b", HtmlUtils.cleanupHtmlSpaces("a&#X20;b"));
    }

    @Test
    void testNumericDecimalZeroWidthSpace() {
        // code point 8203 (U+200B) -> matched by the zero-width part of WHITESPACE_CLASS.
        assertEquals("hello world", HtmlUtils.cleanupHtmlSpaces("hello&#8203;world"));
    }

    // === Mixed / multiple occurrences (loop iterates more than once, both passes interplay) ===

    @Test
    void testMultipleEncodedSpaces() {
        assertEquals("a b c", HtmlUtils.cleanupHtmlSpaces("a&nbsp;b&#160;c"));
    }

    @Test
    void testMixedLiteralAndEncodedWhitespace() {
        assertEquals("a b c", HtmlUtils.cleanupHtmlSpaces("a b&nbsp;c"));
    }

    // === Non-whitespace entities are preserved (isSpace == false -> Matcher.quoteReplacement branch) ===

    @Test
    void testAmpersandEntityPreserved() {
        // "&amp;" does not match HTML_SPACE_ENTITY at all -> handled by appendTail, returned verbatim.
        assertEquals("Table &amp; 1", HtmlUtils.cleanupHtmlSpaces("Table &amp; 1"));
    }

    @Test
    void testNumericAmpersandPreserved() {
        // "&#38;" matches HTML_SPACE_ENTITY but decodes to '&' (non-whitespace) -> preserved via
        // Matcher.quoteReplacement.
        assertEquals("a&#38;b", HtmlUtils.cleanupHtmlSpaces("a&#38;b"));
    }

    @Test
    void testNumericHexNonWhitespacePreserved() {
        // 0x41 = 'A', not whitespace -> preserved.
        assertEquals("a&#x41;b", HtmlUtils.cleanupHtmlSpaces("a&#x41;b"));
    }

    // === Malformed / out-of-range numeric references (decodesToWhitespace catch block) ===

    @Test
    void testMalformedDecimalReferencePreserved() {
        // "&#abc;" matches the pattern (hex digits allowed), but the decimal branch parseInt("abc", 10)
        // throws NumberFormatException -> treated as non-whitespace and preserved.
        assertEquals("x&#abc;y", HtmlUtils.cleanupHtmlSpaces("x&#abc;y"));
    }

    @Test
    void testOutOfRangeCodePointPreserved() {
        // 0xFFFFFF (16777215) exceeds Character.MAX_CODE_POINT; Character.toChars throws
        // IllegalArgumentException -> preserved.
        assertEquals("x&#xFFFFFF;y", HtmlUtils.cleanupHtmlSpaces("x&#xFFFFFF;y"));
    }
}
