package _959.server_waypoint.common.client.render;

//? if <= 1.21 && fabric {
/*import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
*///?}

class WaypointBlockItemAlphaTest {
    //? if <= 1.21 && fabric {
    /*@BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stoneUsesBlendingOnlyInsideTranslucentWaypointScope() throws Exception {
        RenderType stone = ItemBlockRenderTypes.getRenderType(new ItemStack(Items.STONE), true);
        assertSame(Sheets.cutoutBlockSheet(), stone);
        assertSame(stone, select(stone));
        int previous = WaypointItemAlpha.pushWorldItemTint(60);
        try {
            assertSame(Sheets.translucentCullBlockSheet(), select(stone));
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }
        assertSame(stone, select(stone));
    }

    @Test
    void diamondKeepsItsExistingTranslucentRenderType() throws Exception {
        int previous = WaypointItemAlpha.pushWorldItemTint(60);
        try {
            RenderType original = ItemBlockRenderTypes.getRenderType(new ItemStack(Items.DIAMOND), true);
            assertSame(Sheets.translucentCullBlockSheet(), original);
            assertSame(original, select(original));
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }
    }

    @Test
    void fullyOpaqueWaypointKeepsTheOriginalBlockRenderType() throws Exception {
        int previous = WaypointItemAlpha.pushWorldItemTint(255);
        try {
            assertSame(Sheets.cutoutBlockSheet(), select(Sheets.cutoutBlockSheet()));
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }
    }

    private static RenderType select(RenderType original) throws Exception {
        Class<?> mixin = Class.forName("_959.server_waypoint.mixin.WaypointBlockItemAlphaMixin");
        var method = mixin.getDeclaredMethod("serverWaypoint$translucentBlockIcon", RenderType.class);
        method.setAccessible(true);
        return (RenderType) method.invoke(mixin.getDeclaredConstructor().newInstance(), original);
    }
    *///?}
}
