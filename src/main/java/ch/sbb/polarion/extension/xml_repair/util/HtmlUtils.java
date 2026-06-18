package ch.sbb.polarion.extension.xml_repair.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class HtmlUtils {

    // Literal whitespace and zero-width characters that must be treated as a plain space when comparing
    // a caption prefix against its data-sequence: \p{Zs} (all Unicode space separators, incl. nbsp U+00A0),
    // zero-width space/non-joiner/joiner (U+200B-U+200D), word joiner (U+2060) and BOM (U+FEFF).
    private final Pattern WHITESPACE_CLASS = Pattern.compile("[\\p{Zs}\\u200B-\\u200D\\u2060\\uFEFF]");

    // HTML-encoded whitespace: the named non-breaking space entity plus any numeric character reference
    // (decimal "&#160;" or hex "&#xA0;"). We match all numeric references here and later decode them to
    // decide whether each one actually represents a whitespace character (see decodesToWhitespace).
    private final Pattern HTML_SPACE_ENTITY = Pattern.compile("&nbsp;|&#x?[0-9a-fA-F]+;", Pattern.CASE_INSENSITIVE);

    /**
     * Normalizes whitespace in a string to plain spaces so that it can be compared reliably. Handles both
     * literal characters and HTML-encoded whitespace entities.
     *
     * <p>Two independent sources of "invisible" whitespace are covered:
     * <ul>
     *   <li><b>Literal characters</b> ({@link #WHITESPACE_CLASS}): text often originates from copy-paste of
     *       external sources containing non-standard spaces (nbsp, thin space, zero-width space, ...).</li>
     *   <li><b>HTML-encoded entities</b> ({@link #HTML_SPACE_ENTITY}): Polarion stores rich-text content as
     *       HTML, and since Polarion 2606 {@code IModule.setHomePageContent(...)} round-trips that content
     *       through Jsoup (ModuleUtils.getContentParts -&gt; Element.outerHtml()). Jsoup's serializer, under
     *       its default escape mode, rewrites every literal non-breaking space (U+00A0) into the named
     *       entity {@code &nbsp;}. So even content written back with a literal nbsp is re-read as
     *       {@code &nbsp;} afterwards. Polarion 2512 used the Jericho parser and kept the raw substring, so
     *       the entity never appeared. Decoding such entities here keeps an encoded value like
     *       {@code foo&nbsp;bar} comparable with its literal {@code foo bar} form.
     *       (Investigation notes: _POLARION_CODE_2606/nbsp_appears_in_homePageContent.md)</li>
     * </ul>
     *
     * <p>Only whitespace entities are converted; other entities such as {@code &amp;} are left untouched, so
     * callers that compare or persist the still-escaped text are not affected.
     */
    public String cleanupHtmlSpaces(String inputString) {
        // First fold HTML-encoded whitespace entities to a real space, then collapse the literal
        // whitespace/zero-width characters. Order matters: decoded entities become literal spaces, which
        // the second pass leaves as-is, so a single normalized representation results either way.
        String withEntitiesNormalized = replaceHtmlWhitespaceEntities(inputString);
        return WHITESPACE_CLASS.matcher(withEntitiesNormalized).replaceAll(" ");
    }

    /**
     * Replaces HTML whitespace entities with a plain space while preserving all other entities verbatim.
     * {@code &nbsp;} is handled by name; numeric references are decoded and replaced only when they map to
     * an actual whitespace character, so e.g. {@code &#38;} (ampersand) is preserved.
     */
    private String replaceHtmlWhitespaceEntities(String input) {
        Matcher matcher = HTML_SPACE_ENTITY.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String entity = matcher.group();
            boolean isSpace = "&nbsp;".equalsIgnoreCase(entity) || decodesToWhitespace(entity);
            // Matcher.quoteReplacement guards against dollar/backslash characters in a preserved entity
            // being interpreted as a replacement back-reference; whitespace entities collapse to one space.
            matcher.appendReplacement(sb, isSpace ? " " : Matcher.quoteReplacement(entity));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Returns true when a numeric character reference (decimal {@code &#160;} or hex {@code &#xA0;}) decodes
     * to a character in {@link #WHITESPACE_CLASS}. Malformed or out-of-range references are treated as
     * non-whitespace (left untouched) rather than throwing.
     */
    private boolean decodesToWhitespace(String numericEntity) {
        String body = numericEntity.substring(2, numericEntity.length() - 1); // strip "&#" and ";"
        try {
            int codePoint = (body.startsWith("x") || body.startsWith("X"))
                    ? Integer.parseInt(body.substring(1), 16)
                    : Integer.parseInt(body, 10);
            return WHITESPACE_CLASS.matcher(new String(Character.toChars(codePoint))).matches();
        } catch (IllegalArgumentException e) {
            // NumberFormatException (bad digits) and IllegalArgumentException (invalid code point) both
            // mean "not a decodable whitespace reference" -> keep the original text unchanged.
            return false;
        }
    }

}
