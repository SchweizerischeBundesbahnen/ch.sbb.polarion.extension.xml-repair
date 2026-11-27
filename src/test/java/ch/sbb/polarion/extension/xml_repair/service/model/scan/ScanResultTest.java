package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanEntityTest.createScanEntity;
import static org.junit.jupiter.api.Assertions.*;

class ScanResultTest {

    @Test
    void testAddItem() {
        ScanResult result = new ScanResult();
        ScanEntity item = createScanEntity("WI-1");

        result.addItem(item);

        assertEquals(List.of(item), result.getItems());
    }

    @Test
    void testItemsViewIsUnmodifiable() {
        ScanResult result = new ScanResult();
        ScanEntity item = createScanEntity("WI-1");
        List<ScanEntity> items = result.getItems();

        assertThrows(UnsupportedOperationException.class, () -> items.add(item));
        assertTrue(result.getItems().isEmpty());
    }
}
