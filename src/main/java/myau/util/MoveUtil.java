package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class MoveUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * The {x, z} velocity the current movement input would add this tick if the player were NOT
     * sneaking. Mirrors vanilla's moveFlying acceleration (ground friction / air factor). If the
     * input was already slowed by sneaking (x0.3) that slowdown is undone first.
     */
    public static double[] predictMovement() {
        EntityPlayerSP p = mc.thePlayer;
        float forward = p.movementInput.moveForward;
        float strafe = p.movementInput.moveStrafe;
        if (p.movementInput.sneak) {
            forward /= 0.3F;
            strafe /= 0.3F;
        }

        float mag = strafe * strafe + forward * forward;
        if (mag < 1.0E-4F) return new double[]{0.0, 0.0};

        float friction = 0.91F;
        if (p.onGround) {
            BlockPos below = new BlockPos(
                    MathHelper.floor_double(p.posX),
                    MathHelper.floor_double(p.getEntityBoundingBox().minY) - 1,
                    MathHelper.floor_double(p.posZ));
            friction = mc.theWorld.getBlockState(below).getBlock().slipperiness * 0.91F;
        }
        float accel = p.onGround
                ? p.getAIMoveSpeed() * (0.16277136F / (friction * friction * friction))
                : p.jumpMovementFactor;

        mag = MathHelper.sqrt_float(mag);
        if (mag < 1.0F) mag = 1.0F;
        mag = accel / mag;
        strafe *= mag;
        forward *= mag;

        float yawRad = p.rotationYaw * 0.017453292F;
        float sin = MathHelper.sin(yawRad);
        float cos = MathHelper.cos(yawRad);
        return new double[]{strafe * cos - forward * sin, forward * cos + strafe * sin};
    }
}
