package _959.server_waypoint.mixin.xaeros_minimap;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaerosMinimapDropDownWidgetMixinTest {
    @Test
    void configuredTargetCanApplyTheBackgroundHookToThePinnedXaeroBuild() throws Exception {
        ClassNode mixin = new ClassNode();
        try (InputStream bytes = XaerosMinimapDropDownWidgetMixin.class.getResourceAsStream(
                "XaerosMinimapDropDownWidgetMixin.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(mixin, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        AnnotationNode annotation = mixin.invisibleAnnotations.stream()
                .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst().orElseThrow();
        String target = null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals("targets")) {
                target = (String) ((java.util.List<?>) annotation.values.get(i + 1)).get(0);
            }
        }
        assertNotNull(target);
        ClassNode dropdown = new ClassNode();
        try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(target.replace('.', '/') + ".class")) {
            assertNotNull(bytes, "The configured Xaero dropdown target is absent: " + target);
            new ClassReader(bytes).accept(dropdown, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        String optionType = Type.getDescriptor(String.class);
        String componentType = Type.getDescriptor(Component.class);
        boolean capturesOption = false;
        boolean fillsBackground = false;
        for (var method : dropdown.methods) {
            if (!method.name.equals("drawSlot")) {
                continue;
            }
            for (Type argument : Type.getArgumentTypes(method.desc)) {
                capturesOption |= argument.getDescriptor().equals(optionType)
                        || argument.getDescriptor().equals(componentType);
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.startsWith("net/minecraft/client/gui/GuiGraphics")
                        && call.desc.equals("(IIIII)V")) {
                    fillsBackground = true;
                }
            }
        }
        assertTrue(capturesOption, "drawSlot has no supported option argument for the name hook");
        assertTrue(fillsBackground, "drawSlot no longer fills the background targeted by the color hook");
    }

    @Test
    void stringOptionsCaptureOwnershipBeforeRemovingMarkerAndResetForPersonalOptions() throws Exception {
        XaerosMinimapDropDownWidgetMixin mixin = new XaerosMinimapDropDownWidgetMixin();
        Method display = handler("sw$displaySyncedWaypointSetString", String.class);
        Method background = handler("sw$useSyncedWaypointSetBackground", int.class);

        assertEquals("Bases", display.invoke(mixin, "sw\u241FBases"));
        assertEquals(0x7F0D47A1, background.invoke(mixin, -939524096));
        assertEquals(0xDD26C6DA, background.invoke(mixin, -10496));
        assertEquals("Personal", display.invoke(mixin, "Personal"));
        assertEquals(-939524096, background.invoke(mixin, -939524096));
        assertEquals(-10496, background.invoke(mixin, -10496));
    }

    @Test
    void componentOptionsPreserveStyleAndResetOwnershipForPersonalOptions() throws Exception {
        XaerosMinimapDropDownWidgetMixin mixin = new XaerosMinimapDropDownWidgetMixin();
        Method display = handler("sw$displaySyncedWaypointSetComponent", Component.class);
        Method background = handler("sw$useSyncedWaypointSetBackground", int.class);
        Component managed = Component.literal("sw\u241FBases").withStyle(style -> style.withBold(true));

        Component result = (Component) display.invoke(mixin, managed);
        assertEquals("Bases", result.getString());
        assertEquals(managed.getStyle(), result.getStyle());
        assertEquals(0xCC00BCD4, background.invoke(mixin, -922757376));
        Component personal = Component.literal("Personal");
        assertSame(personal, display.invoke(mixin, personal));
        assertEquals(-922757376, background.invoke(mixin, -922757376));
    }

    private static Method handler(String name, Class<?> argument) throws NoSuchMethodException {
        Method method = XaerosMinimapDropDownWidgetMixin.class.getDeclaredMethod(name, argument);
        method.setAccessible(true);
        return method;
    }
}
