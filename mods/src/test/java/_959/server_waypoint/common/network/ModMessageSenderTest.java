package _959.server_waypoint.common.network;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.WaypointDetailsTextBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModMessageSenderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsDetailsForDescriptionContainingLineBreak() {
        String description = "{\"text\":\"\",\"extra\":[{\"text\":\"First line\"},{\"text\":\"\n\"},{\"text\":\"Second line\"}]}";
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Text",
                "{\"text\":\"Text\",\"color\":\"#FEBA7E\"}",
                "12",
                new WaypointPos(39, -61, -30),
                0x39C5BB,
                0,
                false,
                List.of(),
                description
        );
        WaypointList list = new WaypointList("", 0, List.of(waypoint));

        var details = WaypointDetailsTextBuilder.waypointDetails(
                "minecraft:overworld",
                list,
                waypoint,
                true,
                true,
                true,
                true
        );

        assertNotEquals(
                "failed to decode message component",
                ModMessageSender.toVanillaText(details).getString()
        );
    }
}
