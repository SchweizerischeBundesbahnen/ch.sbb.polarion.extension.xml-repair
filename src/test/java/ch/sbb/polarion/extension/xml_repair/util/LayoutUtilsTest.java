package ch.sbb.polarion.extension.xml_repair.util;

import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LayoutUtilsTest {

    @Test
    void testSwitchLayoutIndexFixesTargetDivOnly() {
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-123");

        String content = "<div id=other>some text</div>"
                + "<div params=id=WI-999|layout_workitem_type=requirement>"
                + "<div params=id=WI-123|layout_workitem_type=text>"
                + "<div params=id=WI-456|layout_workitem_type=issue>";
        when(module.getHomePageContent()).thenReturn(Text.html(content));

        LayoutUtils.switchLayoutIndex(module, workItem, 5);

        String expected = "<div id=other>some text</div>"
                + "<div params=id=WI-999|layout_workitem_type=requirement>"
                + "<div params=id=WI-123|layout=0|layout_workitem_type=text>"
                + "<div params=id=WI-456|layout_workitem_type=issue>";
        verify(module).setHomePageContent(Text.html(expected));
        verify(module.getStructureNodeOfWI(workItem)).updateWorkItemLayout(5);
    }

    @Test
    void testSwitchLayoutIndexNoMatchWhenLayoutAlreadyPresent() {
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-123");

        String content = "<div params=id=WI-123|layout=1|layout_workitem_type=text>"
                + "<div params=id=WI-456|layout_workitem_type=issue>";
        when(module.getHomePageContent()).thenReturn(Text.html(content));

        LayoutUtils.switchLayoutIndex(module, workItem, 3);

        verify(module, never()).setHomePageContent(any());
        verify(module.getStructureNodeOfWI(workItem)).updateWorkItemLayout(3);
    }

    @Test
    void testSwitchLayoutIndexNoMatchingDiv() {
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-123");

        String content = "<div params=id=WI-456|layout_workitem_type=issue>"
                + "<div params=id=WI-789|layout_workitem_type=requirement>";
        when(module.getHomePageContent()).thenReturn(Text.html(content));

        LayoutUtils.switchLayoutIndex(module, workItem, 2);

        verify(module, never()).setHomePageContent(any());
        verify(module.getStructureNodeOfWI(workItem)).updateWorkItemLayout(2);
    }
}
