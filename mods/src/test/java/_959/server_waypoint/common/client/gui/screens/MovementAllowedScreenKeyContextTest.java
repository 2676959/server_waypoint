//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

//? if forge || neoforge {
/*import _959.server_waypoint.mixin.BoundKeyAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
//?} else {
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
//?}
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
*///?}

class MovementAllowedScreenKeyContextTest {
    //? if forge || neoforge {
    /*@Test
    void pressedMovementKeysWorkOnlyWhileTheScreenAcceptsMovement() throws Exception {
        TestScreen screen = createScreen();
        KeyMapping key = screen.client().options.keyUp;
        screen.init();
        key.setDown(true);

        assertTrue(key.isDown(), "An open movement screen must not deactivate its movement keys");
        screen.acceptMovementKeys(false);
        assertFalse(key.isDown(), "Text entry must suppress movement");
        screen.acceptMovementKeys(true);
        key.setDown(false);
        assertFalse(key.isDown(), "Releasing a key must stop movement");
    }

    @Test
    void closingAfterARebuildRestoresEveryOriginalContext() throws Exception {
        TestScreen screen = createScreen();
        KeyMapping[] keys = movementKeys(screen.client().options);
        IKeyConflictContext original = keys[0].getKeyConflictContext();
        screen.init();
        screen.init();
        for (KeyMapping key : keys) {
            assertTrue(key.getKeyConflictContext().isActive());
            assertTrue(key.getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME));
        }

        screen.removed();
        for (KeyMapping key : keys) {
            assertSame(original, key.getKeyConflictContext());
        }
    }

    @Test
    void anotherScreenDoesNotActivateTheMovementContexts() throws Exception {
        TestScreen screen = createScreen();
        screen.init();
        setScreen(screen.client(), null);
        screen.client().options.keyUp.setDown(true);

        assertFalse(screen.client().options.keyUp.isDown());
    }

    private static TestScreen createScreen() throws Exception {
        TestScreen screen = allocate(TestScreen.class);
        screen.setClient(allocate(Minecraft.class));
        //? if >=26.2 {
        setField(screen.client(), "gui", allocate(net.minecraft.client.gui.Gui.class));
        //?}
        setScreen(screen.client(), screen);
        Options options = allocate(Options.class);
        setField(screen.client(), "options", options);
        setField(options, "keyUp", new TestKey("forward", InputConstants.KEY_W));
        setField(options, "keyLeft", new TestKey("left", InputConstants.KEY_A));
        setField(options, "keyDown", new TestKey("back", InputConstants.KEY_S));
        setField(options, "keyRight", new TestKey("right", InputConstants.KEY_D));
        setField(options, "keyJump", new TestKey("jump", InputConstants.KEY_SPACE));
        setField(options, "keyShift", new TestKey("sneak", InputConstants.KEY_LSHIFT));
        setField(options, "keySprint", new TestKey("sprint", InputConstants.KEY_LCONTROL));
        IKeyConflictContext inactive = new IKeyConflictContext() {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public boolean conflicts(IKeyConflictContext other) {
                return other == this || other == KeyConflictContext.IN_GAME;
            }
        };
        for (KeyMapping key : movementKeys(options)) {
            key.setKeyConflictContext(inactive);
        }
        screen.acceptMovementKeys(true);
        return screen;
    }

    private static KeyMapping[] movementKeys(Options options) {
        return new KeyMapping[]{options.keyUp, options.keyLeft, options.keyDown, options.keyRight,
                options.keyJump, options.keyShift, options.keySprint};
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setScreen(Minecraft client, TestScreen screen) throws Exception {
        //? if >=26.2 {
        setField(client.gui, "screen", screen);
        //?} else {
        client.screen = screen;
        //?}
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    private static final class TestKey extends KeyMapping implements BoundKeyAccessor {
        private TestKey(String name, int code) {
            //? if >=1.21.9 {
            super("server_waypoint.test." + name, code, KeyMapping.Category.MOVEMENT);
            //?} else {
            super("server_waypoint.test." + name, code, "key.categories.movement");
            //?}
        }

        @Override
        public InputConstants.Key getBoundKey() {
            return getDefaultKey();
        }
    }

    private static final class TestScreen extends MovementAllowedScreen {
        private TestScreen() {
            super(Component.empty());
        }

        private Minecraft client() {
            return this.minecraft;
        }

        private void setClient(Minecraft client) throws Exception {
            var field = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("minecraft");
            field.setAccessible(true);
            field.set(this, client);
        }

        @Override
        protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        int getContentWidth() {
            return 0;
        }

        @Override
        int getContentHeight() {
            return 0;
        }
    }
    *///?}
}
