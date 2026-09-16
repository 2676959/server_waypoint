package _959.server_waypoint.common.client.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientDimensionCatalogTest {
    @Test
    void commandSuggestionsOnlyContributeNamespacedDimensionIdentifiers() {
        assertEquals(
                List.of("minecraft:overworld", "minecraft:the_nether", "example:moon"),
                ClientDimensionCatalog.dimensionNamesFromSuggestions(List.of(
                        "all",
                        "search",
                        "minecraft:the_nether",
                        "example:moon",
                        "minecraft:overworld",
                        "minecraft:overworld",
                        "sort"
                ))
        );
    }

    @Test
    void dimensionSourcesAreMergedWithoutDuplicates() {
        assertEquals(
                List.of("minecraft:overworld", "minecraft:the_end", "example:moon"),
                ClientDimensionCatalog.mergeDimensionNames(
                        List.of("example:moon", "minecraft:overworld"),
                        List.of("minecraft:the_end", "minecraft:overworld")
                )
        );
    }
}
