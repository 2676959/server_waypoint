package _959.server_waypoint.common.network;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.WaypointDetailsTextBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModMessageSenderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable bootstrapFailure) {
            // This test decodes a component through Minecraft's codecs, which need
            // bootstrapped registries. Forge-family unit-test runtimes cannot get
            // there from a plain JUnit run:
            //  - Forge: Bootstrap initialises the Forge network stack, whose event
            //    classes only gain the constructors the event bus needs after
            //    Forge's class transformation.
            //  - NeoForge: SharedConstants consults FMLEnvironment, which throws
            //    because there is no current FML loader.
            // Coverage is retained on every runtime that can bootstrap; skip here
            // instead of failing the build on runtimes that cannot.
            Assumptions.abort(
                    "Minecraft could not be bootstrapped in this test runtime: " + bootstrapFailure
            );
        }
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
