import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Test observer/controller; never registers a bytecode transformer. */
public class Agent {
    public static void agentmain(String payload, Instrumentation instrumentation) throws Exception {
        String[] args = payload.split("\\|", 4);
        Path output = Path.of(args[1]);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> minecraft =
                    Arrays.stream(instrumentation.getAllLoadedClasses())
                            .filter(
                                    type ->
                                            type.getName().equals("net.minecraft.client.Minecraft")
                                                    || type.getName()
                                                            .equals("net.minecraft.class_310"))
                            .findFirst()
                            .orElse(null);
            if (minecraft == null) {
                if (!args[0].equals("screen")) {
                    throw new IllegalStateException("Minecraft has not loaded");
                }
                write(output, "minecraft=not-loaded\n");
                return;
            }
            ClassLoader game = minecraft.getClassLoader();
            // NeoForge's Mixin service resolves dependencies through the thread context loader.
            Thread.currentThread().setContextClassLoader(game);
            Object instance = null;
            for (Field field : minecraft.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == minecraft) {
                    field.setAccessible(true);
                    instance = field.get(null);
                    if (instance != null) {
                        break;
                    }
                }
            }
            if (instance == null) {
                write(output, "minecraft=not-initialized\n");
                return;
            }
            switch (args[0]) {
                case "screen" -> inspectScreen(minecraft, instance, output);
                case "identify" -> {
                    URLClassLoader helper =
                            new URLClassLoader(new URL[] {Path.of(args[2]).toUri().toURL()}, game);
                    Object version =
                            Class.forName("GameApi", true, helper)
                                    .getMethod("version")
                                    .invoke(null);
                    write(output, "PASS\nminecraft=" + version + "\n");
                }
                case "audit" -> write(output, MixinAudit.run(game, Path.of(args[2])));
                case "scenario" -> {
                    URLClassLoader helper =
                            new URLClassLoader(new URL[] {Path.of(args[3]).toUri().toURL()}, game);
                    Class<?> scenarios = Class.forName("LiveChecks", true, helper);
                    scenarios
                            .getMethod("start", String.class, String.class, String.class)
                            .invoke(
                                    null,
                                    output.getParent().toString(),
                                    args[2],
                                    output.toString());
                }
                case "quit" -> {
                    URLClassLoader helper =
                            new URLClassLoader(new URL[] {Path.of(args[2]).toUri().toURL()}, game);
                    Class.forName("LiveChecks", true, helper).getMethod("quit").invoke(null);
                }
                default -> throw new IllegalArgumentException("Unknown action: " + args[0]);
            }
        } catch (Throwable failure) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            write(output, "FAIL\n" + stack);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static void inspectScreen(Class<?> minecraft, Object instance, Path output)
            throws Exception {
        Object owner = instance;
        Class<?> ownerType = minecraft;
        boolean hasScreen =
                Arrays.stream(minecraft.getDeclaredFields())
                        .anyMatch(field -> isScreen(field.getType()));
        if (!hasScreen) {
            Field gui = minecraft.getDeclaredField("gui");
            gui.setAccessible(true);
            owner = gui.get(instance);
            ownerType = gui.getType();
        }
        StringBuilder state = new StringBuilder("minecraft=" + minecraft.getName() + "\n");
        for (Field field : ownerType.getDeclaredFields()) {
            String type = field.getType().getName();
            if (isScreen(field.getType())
                    || type.equals("net.minecraft.client.gui.screens.Overlay")
                    || type.equals("net.minecraft.class_4071")) {
                field.setAccessible(true);
                Object value = field.get(owner);
                String name = value == null ? "null" : value.getClass().getName();
                if (isScreen(field.getType())) {
                    state.append("screen=")
                            .append(name)
                            .append("\n")
                            .append("title=")
                            .append(
                                    name.equals("net.minecraft.client.gui.screens.TitleScreen")
                                            || name.equals("net.minecraft.class_442"))
                            .append("\n");
                } else {
                    state.append("overlay=").append(name).append("\n");
                }
            }
        }
        write(output, state.toString());
    }

    private static boolean isScreen(Class<?> type) {
        return type.getName().equals("net.minecraft.client.gui.screens.Screen")
                || type.getName().equals("net.minecraft.class_437");
    }

    private static void write(Path output, String text) throws Exception {
        Path temporary = output.resolveSibling(output.getFileName() + ".writing");
        Files.writeString(temporary, text);
        Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
    }
}
