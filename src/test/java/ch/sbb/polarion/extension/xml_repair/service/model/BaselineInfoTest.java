package ch.sbb.polarion.extension.xml_repair.service.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineInfoTest {

    @Test
    void testNoArgsConstructorAndSetters() {
        BaselineInfo info = new BaselineInfo();
        info.setRevision("42");
        info.setName("first release");

        assertEquals("42", info.getRevision());
        assertEquals("first release", info.getName());
    }

    @Test
    void testAllArgsConstructor() {
        BaselineInfo info = new BaselineInfo("42", "first release");

        assertEquals("42", info.getRevision());
        assertEquals("first release", info.getName());
    }

    @Test
    void testBuilder() {
        BaselineInfo info = BaselineInfo.builder().revision("100").name("v1.0").build();

        assertEquals("100", info.getRevision());
        assertEquals("v1.0", info.getName());
    }

    @Test
    void testEqualsAndHashCode() {
        BaselineInfo a = new BaselineInfo("42", "rel");
        BaselineInfo b = new BaselineInfo("42", "rel");
        BaselineInfo c = new BaselineInfo("99", "rel");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void testCompareToHigherRevisionComesFirst() {
        BaselineInfo high = new BaselineInfo("200", "newer");
        BaselineInfo low = new BaselineInfo("100", "older");

        assertTrue(high.compareTo(low) < 0);
        assertTrue(low.compareTo(high) > 0);
    }

    @Test
    void testCompareToEqualRevisionsReturnsZero() {
        BaselineInfo a = new BaselineInfo("100", "a");
        BaselineInfo b = new BaselineInfo("100", "b");

        assertEquals(0, a.compareTo(b));
    }

    @Test
    void testCompareToTreatsNonNumericRevisionAsZero() {
        BaselineInfo numeric = new BaselineInfo("100", "valid");
        BaselineInfo nonNumeric = new BaselineInfo("not-a-number", "invalid");

        assertTrue(numeric.compareTo(nonNumeric) < 0);
        assertTrue(nonNumeric.compareTo(numeric) > 0);
    }

    @Test
    void testCompareToBothNonNumericReturnsZero() {
        BaselineInfo a = new BaselineInfo("abc", "a");
        BaselineInfo b = new BaselineInfo("xyz", "b");

        assertEquals(0, a.compareTo(b));
    }

    @Test
    void testCompareToTreatsNullRevisionAsZero() {
        BaselineInfo withNull = new BaselineInfo(null, "n");
        BaselineInfo withRevision = new BaselineInfo("100", "v");

        assertTrue(withRevision.compareTo(withNull) < 0);
        assertTrue(withNull.compareTo(withRevision) > 0);
    }

    @Test
    void testSortingProducesDescendingRevisionOrder() {
        List<BaselineInfo> baselines = new ArrayList<>(List.of(
                new BaselineInfo("100", "b"),
                new BaselineInfo("300", "a"),
                new BaselineInfo("200", "c")
        ));

        baselines.sort(BaselineInfo::compareTo);

        assertEquals("300", baselines.get(0).getRevision());
        assertEquals("200", baselines.get(1).getRevision());
        assertEquals("100", baselines.get(2).getRevision());
    }

    @Test
    void testDefaultsAreNull() {
        BaselineInfo info = new BaselineInfo();

        assertNull(info.getRevision());
        assertNull(info.getName());
    }
}
