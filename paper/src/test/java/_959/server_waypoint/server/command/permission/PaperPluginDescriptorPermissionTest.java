package _959.server_waypoint.server.command.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PaperPermissionManager} applies a {@code CommandPermission} level only while Bukkit reports the node as
 * unset. {@code PermissibleBase.recalculatePermissions()} records every permission whose declared default applies
 * to a player (and its children) as set for that player, so a declaration in {@code paper-plugin.yml} silently
 * replaces the configured level for everyone it applies to. Until this was noticed, {@code navigate} was declared
 * {@code default: true} and {@code "navigate": 2} had no effect on Paper.
 */
class PaperPluginDescriptorPermissionTest {
    private static final String NAVIGATE = "server_waypoint.command.navigate";

    @Test
    void descriptorLeavesEveryCommandNodeUnsetSoTheConfiguredLevelApplies() throws Exception {
        List<Permission> declared;
        try (InputStream descriptor = PaperPluginDescriptorPermissionTest.class.getResourceAsStream("/paper-plugin.yml")) {
            assertNotNull(descriptor, "paper-plugin.yml is not on the test classpath");
            declared = declaredPermissions(new InputStreamReader(descriptor, StandardCharsets.UTF_8));
        }
        List<String> nodes = commandNodes();
        assertTrue(nodes.contains(NAVIGATE), "command nodes were not enumerated: " + nodes);

        for (boolean operator : new boolean[]{false, true}) {
            Set<String> preset = presetNodes(declared, operator);
            for (String node : nodes) {
                assertFalse(preset.contains(node), () -> "paper-plugin.yml pre-sets " + node + " for "
                        + (operator ? "operators" : "non-operators") + ", so PaperPermissionManager would never"
                        + " reach its CommandPermission level. Remove the declaration or use 'default: false'.");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declarations")
    void presetDetectionFollowsBukkitDefaults(String name, String permissions, boolean nonOperator, boolean operator) {
        List<Permission> declared = declaredPermissions(new StringReader(permissions));

        assertEquals(nonOperator, presetNodes(declared, false).contains(NAVIGATE));
        assertEquals(operator, presetNodes(declared, true).contains(NAVIGATE));
    }

    private static Stream<Arguments> declarations() {
        return Stream.of(
                // The declaration this project shipped until the navigate level was found to have no effect.
                Arguments.of("default true", """
                        permissions:
                          server_waypoint.command.navigate:
                            description: Use server-side waypoint navigation
                            default: true
                        """, true, true),
                // Deleting only the default line is not a fix: Bukkit then applies its own default, op.
                Arguments.of("no default key falls back to op", """
                        permissions:
                          server_waypoint.command.navigate:
                            description: Use server-side waypoint navigation
                        """, false, true),
                Arguments.of("default !op", """
                        permissions:
                          server_waypoint.command.navigate:
                            default: '!op'
                        """, true, false),
                Arguments.of("default false", """
                        permissions:
                          server_waypoint.command.navigate:
                            default: false
                        """, false, false),
                Arguments.of("default parent with the node as a child", """
                        permissions:
                          server_waypoint.group:
                            default: true
                            children:
                              server_waypoint.command.navigate: true
                        """, true, true),
                Arguments.of("non-default parent with the node as a child", """
                        permissions:
                          server_waypoint.group:
                            default: false
                            children:
                              server_waypoint.command.navigate: true
                        """, false, false),
                Arguments.of("no permissions section", "name: ServerWaypoint\n", false, false)
        );
    }

    /** The permissions {@code paper-plugin.yml} declares, parsed the way Bukkit parses a plugin's permissions. */
    private static List<Permission> declaredPermissions(Reader descriptor) {
        Map<?, ?> yaml = new Yaml().load(descriptor);
        List<Permission> declared = new ArrayList<>();
        if (yaml != null && yaml.get("permissions") instanceof Map<?, ?> permissions) {
            for (Map.Entry<?, ?> entry : permissions.entrySet()) {
                declared.add(Permission.loadPermission(entry.getKey().toString(), (Map<?, ?>) entry.getValue(),
                        Permission.DEFAULT_PERMISSION, declared));
            }
        }
        return declared;
    }

    /** The nodes {@code PermissibleBase.recalculatePermissions()} would mark as set on a fresh player. */
    private static Set<String> presetNodes(List<Permission> declared, boolean operator) {
        Map<String, Permission> byName = new HashMap<>();
        for (Permission permission : declared) {
            byName.put(lower(permission.getName()), permission);
        }
        Set<String> preset = new HashSet<>();
        for (Permission permission : declared) {
            if (permission.getDefault().getValue(operator)) {
                preset.add(lower(permission.getName()));
                addChildren(permission, byName, preset);
            }
        }
        return preset;
    }

    private static void addChildren(Permission parent, Map<String, Permission> byName, Set<String> preset) {
        for (String child : parent.getChildren().keySet()) {
            if (preset.add(lower(child)) && byName.containsKey(lower(child))) {
                addChildren(byName.get(lower(child)), byName, preset);
            }
        }
    }

    /** Every node the plugin checks, so a newly added key is covered without editing this test. */
    @SuppressWarnings("unchecked")
    private static List<String> commandNodes() throws ReflectiveOperationException {
        PermissionKeys<String> keys = new PermissionStringKeys();
        List<String> nodes = new ArrayList<>();
        for (Method accessor : PermissionKeys.class.getMethods()) {
            if (accessor.getParameterCount() == 0 && accessor.getReturnType() == PermissionKeys.PermissionKey.class) {
                nodes.add(lower(((PermissionKeys<String>.PermissionKey) accessor.invoke(keys)).getKey()));
            }
        }
        return nodes;
    }

    private static String lower(String node) {
        return node.toLowerCase(Locale.ROOT);
    }
}
