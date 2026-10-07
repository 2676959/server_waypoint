import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class MixinAudit {
    public static String run(ClassLoader loader, Path targets) throws Exception {
        StringBuilder report = new StringBuilder();
        List<String> loaded = new ArrayList<>();
        int count = 0;
        for (String entry : Files.readAllLines(targets)) {
            boolean optional = entry.startsWith("OPTIONAL ");
            String name = optional ? entry.substring("OPTIONAL ".length()) : entry;
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException absent) {
                if (!optional) {
                    throw absent;
                }
                report.append("OPTIONAL_ABSENT ").append(name).append('\n');
                continue;
            }
            report.append("TRANSFORMED ").append(name).append('\n');
            loaded.add(name);
            count++;
        }
        Class<?> waypoint = Class.forName("xaero.map.mods.gui.Waypoint", true, loader);
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object marker =
                unsafeClass
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafeField.get(null), waypoint);
        Field setName = waypoint.getDeclaredField("setName");
        setName.setAccessible(true);
        setName.set(marker, "sw\u241FBases");
        Object returnedKey = waypoint.getMethod("getSetName").invoke(marker);
        if (!"sw\u241FBases".equals(returnedKey))
            throw new AssertionError("Native set identity altered: " + returnedKey);
        report.append("PASS native set identity=").append(returnedKey).append('\n');
        Class<?> access =
                Class.forName(
                        "_959.server_waypoint.access.XaerosWorldMapWaypointAccess", true, loader);
        if (!access.isInstance(marker))
            throw new AssertionError("World Map access interface was not applied");
        if (!"sw\u241FBases".equals(access.getMethod("sw$getRawSetName").invoke(marker)))
            throw new AssertionError("Wrong raw set key");
        report.append("PASS World Map mixin access interface\n");
        for (String target :
                List.of(
                        "xaero.map.mods.gui.WaypointReader",
                        loaded.stream()
                                .filter(n -> n.endsWith("DropDownWidget"))
                                .findFirst()
                                .orElseThrow())) {
            Class<?> type = Class.forName(target, false, loader);
            List<String> handlers =
                    Arrays.stream(type.getDeclaredMethods())
                            .map(Method::getName)
                            .filter(n -> n.contains("sw$"))
                            .toList();
            if (handlers.isEmpty())
                throw new AssertionError("No Server Waypoint handlers in " + target);
            report.append("PASS applied handlers ")
                    .append(target)
                    .append(' ')
                    .append(handlers)
                    .append('\n');
            for (Class<?> inner : type.getDeclaredClasses()) {
                Class.forName(inner.getName(), false, loader);
            }
        }
        Class<?> environmentClass =
                Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", true, loader);
        Object environment = environmentClass.getMethod("getCurrentEnvironment").invoke(null);
        environmentClass.getMethod("audit").invoke(environment);
        report.append("PASS MixinEnvironment.audit\nSUCCESS targets=").append(count).append('\n');
        return "PASS\n" + report;
    }
}
