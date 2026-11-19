package _959.server_waypoint.common.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTeleportKeyTest {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @Test
    void teleportUsesTByDefaultAndMatchesOnlyTheReboundKey() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var t = InputConstants.getKey("key.keyboard.t");
        var escape = InputConstants.getKey("key.keyboard.escape");
        assertEquals(InputConstants.KEY_T, config.getTeleportKey().getValue());
        assertTrue(config.matchesTeleportKey(t));

        config.setTeleportKey(escape);
        ClientConfig restored = GSON.fromJson(GSON.toJson(config), ClientConfig.class);
        assertEquals(escape, restored.getTeleportKey());
        assertFalse(restored.matchesTeleportKey(t));
        assertTrue(restored.matchesTeleportKey(escape));

        restored.setTeleportMethod(ClientConfig.TeleportMethod.RIGHT_CLICK);
        assertFalse(restored.matchesTeleportKey(escape));
    }

    @Test
    void invalidAndMouseKeyNamesFallBackToTheDefault() {
        for (String invalid : new String[]{"null", "\"invalid\"", "\"key.keyboard\"", "\"key.mouse.left\""}) {
            ClientConfig config = GSON.fromJson("{\"teleportKey\":" + invalid + "}", ClientConfig.class);
            assertEquals(InputConstants.KEY_T, config.getTeleportKey().getValue());
        }
    }
}
