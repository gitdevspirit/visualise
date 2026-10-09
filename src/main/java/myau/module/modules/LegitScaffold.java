package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.BlockUtil;
import myau.util.KeyBindUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Legit scaffold (bridge assist). It never places blocks for you and never touches the
 * place key. It only
 *  - holds sneak for you while you are about to walk off an edge, and lets go once you are
 *    back over solid ground (with a human-like release delay), and
 *  - optionally nudges your pitch toward an angle that lets you place against the side of the
 *    block you are standing on ("Pre Place").
 *
 * Ported from the Keystrokes-mod BridgeAssist module. Visualise has no pre-input or client
 * rotation event, so this version drives the sneak key state from the pre-tick event, predicts
 * the next-tick position itself, and applies the pre-place pitch to the player directly.
 */
public class LegitScaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final EnumFacing[] SIDES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    public final BooleanSetting prePlace = register(new BooleanSetting("Pre Place", false));
    public final SliderSetting edgeOffset = register(new SliderSetting("Edge Offset", 0.0, 0.0, 0.3, 0.01));
    public final SliderSetting unsneakDelay = register(new SliderSetting("Unsneak Delay (ms)", 50, 50, 300, 5));
    public final SliderSetting sneakOnJump = register(new SliderSetting("Sneak On Jump (ms)", 0, 0, 500, 5));
    public final BooleanSetting sneakKeyPressed = register(new BooleanSetting("Require Sneak Key", false));
    public final BooleanSetting holdingBlocks = register(new BooleanSetting("Only Holding Blocks", false));
    public final BooleanSetting lookingDown = register(new BooleanSetting("Only Looking Down", false));
    public final BooleanSetting notMovingForward = register(new BooleanSetting("Not Moving Forward", false));

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;

    // Per-tick input snapshot (what PrePlayerInputEvent used to hand us).
    private float inForward;
    private float inStrafe;
    private boolean inJump;
    // null = leave the sneak key alone this tick, otherwise force it to this value.
    private Boolean sneakOverride;
    // True while we are holding the sneak key state away from the physical key.
    private boolean keyOverridden;

    public LegitScaffold() {
        super("LegitScaffold", false);
    }

    @Override
    public String[] getSuffix() {
        double offset = edgeOffset.getValue();
        String text = offset == Math.rint(offset)
                ? Integer.toString((int) offset)
                : Double.toString(Math.round(offset * 100.0) / 100.0);
        return new String[]{text};
    }

    @Override
    public void onDisabled() {
        sneakingFromModule = false;
        placed = false;
        forceRelease = false;
        resetUnsneak();
        releaseKeyControl();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        readInput();
        sneakOverride = null;
        processInput();
        applySneak();

        if (prePlace.getValue()) {
            doPrePlace();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) {
            return;
        }
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) event.getPacket();
            if (c08.getPlacedBlockDirection() != 255 && sneakingFromModule && sneakKeyPressed.getValue()) {
                placed = true;
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // input handling
    // ------------------------------------------------------------------------------------

    private void readInput() {
        inForward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
        inStrafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindRight.isKeyDown() ? 1 : 0);
        inJump = mc.gameSettings.keyBindJump.isKeyDown();
    }

    private void processInput() {
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) {
            return;
        }

        boolean manualSneak = isManualSneak();
        boolean requireSneak = sneakKeyPressed.getValue();

        if (manualSneak && !requireSneak) {
            resetUnsneak();
            return;
        }

        if (requireSneak && (!manualSneak || (inForward == 0 && inStrafe == 0))) {
            if (!manualSneak) resetUnsneak();
            repressSneak();
            return;
        }

        if (notMovingForward.getValue() && inForward > 0) {
            clearSneak();
            return;
        }
        if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) {
            clearSneak();
            return;
        }
        if (holdingBlocks.getValue()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                clearSneak();
                return;
            }
        }

        if (inJump && mc.thePlayer.onGround && (inForward != 0 || inStrafe != 0) && sneakOnJump.getValue() > 0) {
            if (!requireSneak || forceRelease) {
                sneakJumpStartTick = mc.thePlayer.ticksExisted;
                double raw = sneakOnJump.getValue() / 50.0;
                int base = (int) raw;
                sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
                pressSneak(true);
                return;
            }
        }

        double offset = computeEdgeOffset(predictBox());

        if (Double.isNaN(offset)) {
            if (inJump && (sneakOnJump.getValue() <= 0 || (inForward == 0 && inStrafe == 0))) {
                if (sneakingFromModule) tryReleaseSneak(true);
            } else if (mc.thePlayer.onGround) {
                pressSneak(true);
            } else if (sneakingFromModule) {
                tryReleaseSneak(true);
            }
            return;
        }

        if (offset > edgeOffset.getValue()) {
            pressSneak(true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(true);
        }
    }

    private void pressSneak(boolean resetDelay) {
        sneakOverride = true;
        sneakingFromModule = true;
        if (resetDelay) unsneakStartTick = -1;
        repressSneak();
    }

    private void tryReleaseSneak(boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
            unsneakStartTick = existed;
            double raw = (unsneakDelay.getValue() - 50) / 50.0;
            int base = (int) raw;
            unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
        }

        if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(false);
            return;
        }
        if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(false);
            return;
        }

        releaseSneak(resetDelay);
    }

    private void releaseSneak(boolean resetDelay) {
        if (!sneakKeyPressed.getValue()) {
            sneakOverride = false;
        } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
            sneakOverride = false;
            forceRelease = true;
        } else if (forceRelease) {
            sneakOverride = false;
        }

        sneakingFromModule = false;
        placed = false;
        if (resetDelay) resetUnsneak();
    }

    private void repressSneak() {
        if (forceRelease && isManualSneak()) {
            sneakOverride = true;
        }
        forceRelease = false;
    }

    private void clearSneak() {
        sneakingFromModule = false;
        resetUnsneak();
        if (sneakKeyPressed.getValue()) repressSneak();
    }

    private void resetUnsneak() {
        unsneakStartTick = -1;
        sneakJumpStartTick = -1;
        sneakJumpDelayTicks = -1;
        unsneakDelayTicks = -1;
    }

    private boolean isManualSneak() {
        return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    /** Writes the decision into the sneak key state, or hands the key back to the real keyboard. */
    private void applySneak() {
        int code = mc.gameSettings.keyBindSneak.getKeyCode();
        if (sneakOverride != null) {
            KeyBindUtil.setKeyBindState(code, sneakOverride);
            keyOverridden = true;
        } else {
            releaseKeyControl();
        }
    }

    private void releaseKeyControl() {
        if (keyOverridden && mc.gameSettings != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
        keyOverridden = false;
    }

    // ------------------------------------------------------------------------------------
    // next-tick prediction + edge distance
    // ------------------------------------------------------------------------------------

    /**
     * Where the player's box will be after this tick's movement if they are NOT sneaking.
     * Mirrors vanilla's moveEntityWithHeading acceleration; wall collisions are ignored.
     */
    private AxisAlignedBB predictBox() {
        EntityPlayerSP p = mc.thePlayer;
        float forward = inForward;
        float strafe = inStrafe;
        if (p.isUsingItem() && !p.isRiding()) {
            forward *= 0.2F;
            strafe *= 0.2F;
        }

        double mx = Math.abs(p.motionX) < 0.005 ? 0.0 : p.motionX;
        double mz = Math.abs(p.motionZ) < 0.005 ? 0.0 : p.motionZ;
        float yawRad = p.rotationYaw * 0.017453292F;
        float sin = MathHelper.sin(yawRad);
        float cos = MathHelper.cos(yawRad);

        if (inJump && p.onGround && p.isSprinting()) {
            mx -= sin * 0.2F;
            mz += cos * 0.2F;
        }

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

        float mag = strafe * strafe + forward * forward;
        if (mag >= 1.0E-4F) {
            mag = MathHelper.sqrt_float(mag);
            if (mag < 1.0F) mag = 1.0F;
            mag = accel / mag;
            strafe *= mag;
            forward *= mag;
            mx += strafe * cos - forward * sin;
            mz += forward * cos + strafe * sin;
        }

        return p.getEntityBoundingBox().offset(mx, 0.0, mz);
    }

    /** Distance from the box's centre to the nearest ground block edge, or NaN if nothing is below. */
    private double computeEdgeOffset(AxisAlignedBB simBox) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                simBox.minX, simBox.minY - 0.01, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ
        );

        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) return Double.NaN;

        double feetX = (simBox.minX + simBox.maxX) / 2.0;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0;

        double minDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            minDist = Math.min(minDist, Math.max(dx, dz));
        }
        return minDist;
    }

    // ------------------------------------------------------------------------------------
    // pre-place pitch assist
    // ------------------------------------------------------------------------------------

    private void doPrePlace() {
        EntityPlayerSP p = mc.thePlayer;
        if (mc.currentScreen != null || p.capabilities.isFlying) return;

        ItemStack held = p.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;
        if (lookingDown.getValue() && p.rotationPitch < 70f) return;
        if (notMovingForward.getValue() && inForward > 0f) return;

        double reach = mc.playerController.getBlockReachDistance();
        float targetPitch = findTargetPitch(p.rotationPitch, reach);
        if (Float.isNaN(targetPitch)) return;

        float delta = targetPitch - p.rotationPitch;
        float maxStep = 3.0f + (float) (Math.random() * 2.0);
        float step = Math.max(-maxStep, Math.min(maxStep, delta * 0.4f));
        p.rotationPitch = MathHelper.clamp_float(p.rotationPitch + step, -90.0f, 90.0f);
    }

    /** Pitch (60..90) that points at a side face of a block under the player, closest to the current pitch. */
    private float findTargetPitch(float currentPitch, double reach) {
        float yaw = mc.thePlayer.rotationYaw;

        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(bbox.minY) - 1;
        int minX = MathHelper.floor_double(bbox.minX);
        int maxX = MathHelper.floor_double(bbox.maxX);
        int minZ = MathHelper.floor_double(bbox.minZ);
        int maxZ = MathHelper.floor_double(bbox.maxZ);

        List<FaceTarget> targets = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (BlockUtil.isReplaceable(standBlock)) continue;
                for (EnumFacing face : SIDES) {
                    if (!BlockUtil.isReplaceable(standBlock.offset(face))) continue;
                    targets.add(new FaceTarget(standBlock, face));
                }
            }
        }
        if (targets.isEmpty()) return Float.NaN;

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        float randScale = 0.2f;

        for (float pitch = 60f; pitch <= 90f; ) {
            float step = 1.0f + (float) (Math.random() * 2 - 1) * (0.3f + randScale * 0.4f);
            if (step < 0.4f) step = 0.4f;
            if (step > 1.8f) step = 1.8f;
            pitch += step;
            float samplePitch = Math.min(pitch, 90f);

            MovingObjectPosition mop = RotationUtil.rayTrace(yaw, samplePitch, reach, 1.0f);
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                EnumFacing hitFace = mop.sideHit;
                if (hitFace != EnumFacing.UP && hitFace != EnumFacing.DOWN) {
                    BlockPos hitBlock = mop.getBlockPos();
                    for (FaceTarget t : targets) {
                        if (hitBlock.equals(t.block) && hitFace == t.face) {
                            float delta = Math.abs(samplePitch - currentPitch);
                            if (delta < bestDelta) {
                                bestDelta = delta;
                                bestPitch = samplePitch;
                            }
                            break;
                        }
                    }
                }
            }
            if (pitch >= 90f) break;
        }
        return bestPitch;
    }

    private static class FaceTarget {
        final BlockPos block;
        final EnumFacing face;

        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }
}
