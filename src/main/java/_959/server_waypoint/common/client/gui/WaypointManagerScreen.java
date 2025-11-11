package _959.server_waypoint.common.client.gui;

import _959.server_waypoint.common.client.WaypointClient;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import _959.server_waypoint.common.client.gui.widgets.NewWaypointListWidget;
import _959.server_waypoint.common.client.gui.widgets.WaypointListWidget;
import _959.server_waypoint.mixin.BoundKeyAccessor;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class WaypointManagerScreen extends Screen {
    private final WaypointClient waypointClient;
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

    public WaypointManagerScreen(Text title, WaypointClient waypointClient) {
        super(title);
        this.waypointClient = waypointClient;
    }

    @Override
    protected void init() {
        int WIDTH = 240;
        int centerX = this.width / 2 - 125;
        this.dimensionListWidget = new DimensionListWidget(centerX, 8, WIDTH, 40, this.textRenderer);
//        this.waypointListWidget = new WaypointListWidget(this.client, waypointClient, this, WIDTH, 300, 48, 20);
        this.waypointListWidget = new NewWaypointListWidget(centerX, 48, WIDTH, 200, this.textRenderer);
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.centerWidgets();
        this.waypointListWidget.render(context, mouseX, mouseY, delta);
        this.dimensionListWidget.render(context, mouseX, mouseY, delta);
    }

    public void updateWaypoints() {

    }

    public void updateWaypoint() {

    }

    public void removeWaypoint() {

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
        this.waypointClient.setScreenUpdater(() -> {});
    }
}
