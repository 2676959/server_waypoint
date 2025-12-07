package _959.server_waypoint.common.client.gui;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import _959.server_waypoint.common.client.gui.widgets.NewWaypointListWidget;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.mixin.BoundKeyAccessor;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.*;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public class WaypointManagerScreen extends Screen {
    private static ScreenState STATE = ScreenState.DEFAULT;
    private final WaypointClientMod waypointClientMod;
    private NewWaypointListWidget waypointListWidget;
//    private WaypointListWidget waypointListWidget;
    private DimensionListWidget dimensionListWidget;
    private KeyBinding forwardKeyBinding;
    private KeyBinding leftKeyBinding;
    private KeyBinding backKeyBinding;
    private KeyBinding rightKeyBinding;
    private KeyBinding jumpKeyBinding;
    private KeyBinding sneakKeyBinding;
    private KeyBinding sprintKeyBinding;
    private InputUtil.Key forwardKey;
    private InputUtil.Key leftKey;
    private InputUtil.Key backKey;
    private InputUtil.Key rightKey;
    private InputUtil.Key jumpKey;
    private InputUtil.Key sneakKey;
    private InputUtil.Key sprintKey;
    private int forwardKeyCode;
    private int leftKeyCode;
    private int backKeyCode;
    private int rightKeyCode;
    private int jumpKeyCode;
    private int sneakKeyCode;
    private int sprintKeyCode;

    public WaypointManagerScreen(Text title, WaypointClientMod waypointClientMod) {
        super(title);
        this.waypointClientMod = waypointClientMod;
        STATE = ScreenState.DEFAULT;
    }

//    public static boolean needUpdate() {
//        return NEED_UPDATE;
//    }

    public static void requestUpdate() {
        STATE = ScreenState.NEED_UPDATE;
    }


    @Override
    protected void init() {
        LOGGER.info("gui init");
        int WIDTH = 240;
        int centerX = this.width / 2 - 125;

        Set<String> dimensionNames;
        List<WaypointList> defaultWaypointLists;

        if (this.waypointClientMod.hasNoWaypoints()) {
            STATE = ScreenState.NO_WAYPOINTS;
            dimensionNames = new TreeSet<>();
            dimensionNames.add("empty");
            this.dimensionListWidget = new DimensionListWidget(centerX, 8, WIDTH, 40, this.textRenderer, dimensionNames, (index) -> {});
            this.waypointListWidget = new NewWaypointListWidget(centerX, 48, WIDTH, 200, this.textRenderer, new ArrayList<>());
            this.waypointListWidget.setEmpty();
        } else {
            dimensionNames = this.waypointClientMod.getDimensionNames();
            defaultWaypointLists = this.waypointClientMod.getDefaultWaypointLists();
            this.dimensionListWidget = new DimensionListWidget(centerX, 8, WIDTH, 40, this.textRenderer, dimensionNames, this::onSelectDimension);
            this.waypointListWidget = new NewWaypointListWidget(centerX, 48, WIDTH, 200, this.textRenderer, defaultWaypointLists);
            this.dimensionListWidget.setDimensionName(WaypointClientMod.getCurrentDimensionName());
        }

        this.addDrawableChild(waypointListWidget);
        this.addDrawableChild(dimensionListWidget);

        forwardKeyBinding = this.client.options.forwardKey;
        leftKeyBinding = this.client.options.leftKey;
        backKeyBinding = this.client.options.backKey;
        rightKeyBinding = this.client.options.rightKey;
        jumpKeyBinding = this.client.options.jumpKey;
        sneakKeyBinding = this.client.options.sneakKey;
        sprintKeyBinding = this.client.options.sprintKey;

        forwardKey = ((BoundKeyAccessor) forwardKeyBinding).getBoundKey();
        leftKey = ((BoundKeyAccessor) leftKeyBinding).getBoundKey();
        backKey = ((BoundKeyAccessor) backKeyBinding).getBoundKey();
        rightKey = ((BoundKeyAccessor) rightKeyBinding).getBoundKey();
        jumpKey = ((BoundKeyAccessor) jumpKeyBinding).getBoundKey();
        sneakKey = ((BoundKeyAccessor) sneakKeyBinding).getBoundKey();
        sprintKey = ((BoundKeyAccessor) sprintKeyBinding).getBoundKey();

        forwardKeyCode = forwardKey.getCode();
        leftKeyCode = leftKey.getCode();
        backKeyCode = backKey.getCode();
        rightKeyCode = rightKey.getCode();
        jumpKeyCode = jumpKey.getCode();
        sneakKeyCode = sneakKey.getCode();
        sprintKeyCode = sprintKey.getCode();
    }

    private void centerWidgets() {
        this.waypointListWidget.setX(this.width / 2 - 125);
        this.dimensionListWidget.setX(this.width / 2 - 125);
    }

    private void onSelectDimension(int index) {
        List<String> dimensionNames = this.dimensionListWidget.dimensionNames;
        if (index < dimensionNames.size()) {
            String selectedDimension = dimensionNames.get(index);
            WaypointFileManager waypointListManager = this.waypointClientMod.getWaypointFileManager(selectedDimension);
            if (waypointListManager != null) {
                this.waypointListWidget.updateWaypointLists(waypointListManager.getWaypointLists());
            } else {
                this.waypointListWidget.setEmpty();
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        switch (STATE) {
            case DEFAULT: {
                this.centerWidgets();
                this.waypointListWidget.render(context, mouseX, mouseY, delta);
                this.dimensionListWidget.render(context, mouseX, mouseY, delta);
                break;
            }
            case NEED_UPDATE: {
                this.centerWidgets();
                this.waypointListWidget.render(context, mouseX, mouseY, delta);
                this.dimensionListWidget.render(context, mouseX, mouseY, delta);
                context.fill(0, 0, width, height, 0xAA000000);
                context.drawText(textRenderer, "Click to update", this.width/2, this.height/2, 0xFFFFFFFF, true);
                break;
            }
            case NO_WAYPOINTS: {
                context.fill(0, 0, width, height, 0xAA000000);
                context.drawText(textRenderer, "No waypoints on this server", this.width/2, this.height/2, 0xFFFFFFFF, true);
                break;
            }
            case NO_SERVER: {
                context.fill(0, 0, width, height, 0xAA000000);
                context.drawText(textRenderer, "Unsupported server", this.width/2, this.height/2, 0xFFFFFFFF, true);
                break;
            }
        }
    }

    public void updateContent() {
        LOGGER.info("updateContent");
        this.dimensionListWidget.updateDimensionNames(this.waypointClientMod.getDimensionNames());
        RegistryKey<World> dimensionKey = this.client.world.getRegistryKey();
        WaypointFileManager waypointListManager;
        if (dimensionKey == null) {
            waypointListManager = this.waypointClientMod.getFileManagerMap().values().iterator().next();
        } else {
            String currentDimension = dimensionKey.toString();
            waypointListManager = this.waypointClientMod.getWaypointFileManager(currentDimension);
            if  (waypointListManager == null) {
                waypointListManager = this.waypointClientMod.getFileManagerMap().values().iterator().next();
            }
        }
        this.waypointListWidget.updateWaypointLists(waypointListManager.getWaypointLists());
        this.dimensionListWidget.setDimensionName(WaypointClientMod.getCurrentDimensionName());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        LOGGER.info("mouseClicked: {}", button);
        if (STATE == ScreenState.NEED_UPDATE) {
            this.updateContent();
            STATE = ScreenState.DEFAULT;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (STATE == ScreenState.NEED_UPDATE) {
            return false;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ret = false;
        if (keyCode == forwardKeyCode || scanCode == forwardKeyCode) {
            forwardKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(forwardKey);
            ret = true;
        } else if (keyCode == leftKeyCode || scanCode == leftKeyCode) {
            leftKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(leftKey);
            ret = true;
        } else if (keyCode == backKeyCode || scanCode == backKeyCode) {
            backKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(backKey);
            ret = true;
        } else if (keyCode == rightKeyCode || scanCode == rightKeyCode) {
            rightKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(rightKey);
            ret = true;
        } else if (keyCode == jumpKeyCode || scanCode == jumpKeyCode) {
            jumpKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(jumpKey);
            ret = true;
        } else if (keyCode == sneakKeyCode || scanCode == sneakKeyCode) {
            sneakKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(sneakKey);
            ret = true;
        } else if (keyCode == sprintKeyCode || scanCode == sprintKeyCode) {
            sprintKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(sprintKey);
            ret = true;
        }
        boolean ret2 = super.keyPressed(keyCode, scanCode, modifiers);
        return ret || ret2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
    }

    public enum ScreenState {
        DEFAULT,
        NEED_UPDATE,
        NO_WAYPOINTS,
        NO_SERVER
    }
}
