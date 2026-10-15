package _959.server_waypoint.common.client.gui.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiIconTextureTest {
    @Test
    void guiIconsAreWhiteMasksWithTransparentBackgrounds() throws IOException {
        for (String name : new String[]{"add", "arrow_downward", "arrow_upward", "clear", "confirm_delete",
                "cube", "delete", "edit", "hide", "home", "lan_servers", "list_arrow", "list_collapse",
                "list_empty", "list_expand", "menu", "navigation", "palette", "placeholder", "reset",
                "segment", "show", "sort_by_alpha", "stacks", "synced"}) {
            var resource = getClass().getResource("/assets/server_waypoint/textures/gui/" + name + ".png");
            assertNotNull(resource, name);
            BufferedImage image = ImageIO.read(resource);
            assertEquals(64, image.getWidth(), name);
            assertEquals(64, image.getHeight(), name);
            boolean visible = false;
            boolean transparent = false;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    if ((pixel >>> 24) == 0) {
                        transparent = true;
                    } else {
                        visible = true;
                        assertEquals(0xFFFFFF, pixel & 0xFFFFFF, name + " must retain the supplied tint");
                    }
                }
            }
            assertTrue(visible, name);
            assertTrue(transparent, name);
        }
    }
}
