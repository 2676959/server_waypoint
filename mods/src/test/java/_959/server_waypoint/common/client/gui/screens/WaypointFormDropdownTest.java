package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row above Name can hold a dropdown of choices as well as a combobox, and the form routes its clicks
 * the same way: its popup takes them before the rows it covers, and a click elsewhere closes it.
 */
class WaypointFormDropdownTest {
    @BeforeAll
    static void bootstrapRegistries() {
        //? if fabric {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        //?} else {
        /*org.junit.jupiter.api.Assumptions.abort("A form needs Minecraft's registries, which plain JUnit bootstraps only on Fabric");
        *///?}
    }

    @Test
    void aDropdownRowTakesClicksOnItsPopupAndAClickElsewhereClosesIt() throws Exception {
        List<String> chosen = new ArrayList<>();
        WaypointFormTestForm.withClient(new TestFont(), () -> {
            Dropdown dropdown = new Dropdown(chosen);
            WaypointFormTestForm form = new WaypointFormTestForm(null, List.of(
                    new AbstractWaypointPropertiesScreen.LeadingRow(
                            AbstractWaypointPropertiesScreen.FormField.DESTINATION, dropdown, dropdown::setWidth)))
                    .layOut(400, 400);
            int x = dropdown.getX() + 5;
            int y = dropdown.getY() + 5;

            assertTrue(form.click(x, y), "a click on the trigger opens the popup");
            assertTrue(dropdown.isExpanded());
            // The rows follow the trigger, each as high as it: One, then Two.
            assertTrue(form.click(x, y + 2 * dropdown.getHeight()));
            assertEquals(List.of("Two"), chosen);
            assertFalse(dropdown.isExpanded());

            assertTrue(form.click(x, y));
            assertTrue(dropdown.isExpanded());
            form.click(1, 1);
            assertFalse(dropdown.isExpanded(), "a click away from an open popup closes it");
        });
    }

    private static final class Dropdown extends TextChoiceDropdown {
        private Dropdown(List<String> chosen) {
            super(100, Component.literal("Choices"), new TestFont());
            this.setRenderPopupSeparately(true);
            for (String label : List.of("One", "Two", "Three")) {
                this.addMenuItem(new TextChoice(Component.literal(label), () -> chosen.add(label)));
            }
        }

        @Override
        protected Component triggerLabel() {
            return Component.literal("Choices");
        }
    }
}
