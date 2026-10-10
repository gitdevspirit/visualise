package myau.util;

import net.minecraft.client.Minecraft;

public class PlayerUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * True when the player's box, shifted by (x, z) and one block down, has no solid block under it,
     * i.e. moving by (x, z) would take the player off the edge they are standing on.
     */
    public static boolean canMove(double x, double z) {
        return mc.theWorld.getCollidingBoundingBoxes(
                mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().offset(x, -1.0, z)
        ).isEmpty();
    }
}
