//~ gui_graphics_26
package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.SuggestingTextInput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//? if >= 1.21.6
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
//? if < 1.21.6
/*import net.minecraft.util.FormattedCharSequence;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;
import net.minecraft.client.gui.components.EditBox;

/** Applies the runtime text-shadow theme only to Server Waypoint's text-input family. */
@Mixin(EditBox.class)
public abstract class EditBoxTextShadowMixin {
    //? if >= 1.21.6 {
    @ModifyExpressionValue(
            method = /*? >= 26 {*/ "extractWidgetRenderState" /*?} else {*//* "renderWidget" *//*?}*/,
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;textShadow:Z", opcode = Opcodes.GETFIELD))
    private boolean sw$themeTextShadow(boolean original) {
        return sw$resolveTextShadow(this, original);
    }
    //?}

    // Vanilla's placeholder ignores its textShadow field, even on recent versions.
    //? if >= 1.21.6 {
    @WrapOperation(method = /*? >= 26 {*/ "extractWidgetRenderState" /*?} else {*//* "renderWidget" *//*?}*/,
            at = @At(value = "INVOKE", target =
                    //? if >= 26 {
                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
                    //?} else {
                    /*"Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
                    *///?}
            ))
    private void sw$themePlaceholder(GuiGraphicsExtractor graphics, Font font, Component text,
                                    int x, int y, int color, Operation<Void> original) {
        if ((Object) this instanceof SuggestingTextInput) {
            graphics./*? >= 26 {*/text/*?} else {*//*drawString*//*?}*/(font, text, x, y, color, sw$resolveTextShadow(this, true));
        } else {
            original.call(graphics, font, text, x, y, color);
        }
    }
    //?} else {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int sw$themePlaceholder(GuiGraphics graphics, Font font, Component text,
                                   int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof SuggestingTextInput
                ? graphics.drawString(font, text, x, y, color, sw$resolveTextShadow(this, true))
                : original.call(graphics, font, text, x, y, color);
    }
    *///?}

    // Older EditBox renderers use the always-shadowed overload for text and the append cursor.
    //? if < 1.21.6 {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int sw$themeFormattedText(GuiGraphics graphics, Font font, FormattedCharSequence text,
                                     int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof SuggestingTextInput
                ? graphics.drawString(font, text, x, y, color, sw$resolveTextShadow(this, true))
                : original.call(graphics, font, text, x, y, color);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int sw$themeStringText(GuiGraphics graphics, Font font, String text,
                                  int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof SuggestingTextInput
                ? graphics.drawString(font, text, x, y, color, sw$resolveTextShadow(this, true))
                : original.call(graphics, font, text, x, y, color);
    }
    *///?}

    @Unique
    private static boolean sw$resolveTextShadow(Object widget, boolean original) {
        return original && (!(widget instanceof SuggestingTextInput)
                || WidgetThemeManager.getBoolean(WidgetThemeVariable.TEXT_SHADOW));
    }
}
