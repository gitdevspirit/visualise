package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AimAssist, rebuilt from the keystrokesmod reference. Each tick (while the conditions are met) it
 * picks the best target, picks an aim point on it (optionally spread over the hitbox) and smoothly
 * turns the real camera toward that point.
 *
 * The reference also has a "Silent" mode that rewrites the rotation sent to the server through a
 * ClientRotationEvent + RotationHelper movement fix. Visualise has neither, so only the Normal
 * (real camera) mode is implemented. The reference also yields to KillAura / BedAura; those modules
 * do not exist here.
 */
public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting   speed = register(new SliderSetting("Speed", 10, 1, 30, 1));
    public final SliderSetting   multipointHorizontal = register(new SliderSetting("Multipoint Horizontal", 0, 0, 100, 1));
    public final SliderSetting   multipointVertical   = register(new SliderSetting("Multipoint Vertical", 0, 0, 100, 1));
    public final SliderSetting   randomization = register(new SliderSetting("Randomization", 50, 0, 100, 1));
    public final SliderSetting   fov   = register(new SliderSetting("FOV", 90, 15, 360, 1));
    public final SliderSetting   range = register(new SliderSetting("Range", 4.5, 0.0, 5.0, 0.1));
    public final DropdownSetting sortMode = register(new DropdownSetting("Sort", 1, "Health", "Angle", "Hurt Time", "Distance"));

    public final BooleanSetting ignoreBehindWalls    = register(new BooleanSetting("Ignore Behind Walls", false));
    public final BooleanSetting ignoreBehindEntities = register(new BooleanSetting("Ignore Behind Entities", false));
    public final BooleanSetting aimInvis         = register(new BooleanSetting("Aim Invisible", false));
    public final BooleanSetting clickAim         = register(new BooleanSetting("Require Mouse", true));
    public final BooleanSetting ignoreTeammates  = register(new BooleanSetting("Ignore Teammates", true));
    public final BooleanSetting stopWhenBreaking = register(new BooleanSetting("Stop When Breaking", false));
    public final SliderSetting  hoverDelay = register(new SliderSetting(
            "Hover Delay", 100, 0, 500, 10, () -> stopWhenBreaking.getValue()));
    public final BooleanSetting weaponOnly = register(new BooleanSetting("Weapon Only", false));

    private long miningStartTime = -1L;

    public AimAssist() { super("AimAssist", false); }

    @Override
    public void onDisabled() {
        miningStartTime = -1L;
    }

    // ── per-tick update (reference: onUpdate) ───────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!conditionsMet()) return;

        EntityPlayer target = getEnemy();
        if (target == null) return;

        float[] rot = getRotationsToTarget(target);
        if (rot == null) return;

        mc.thePlayer.rotationYaw     = rot[0];
        mc.thePlayer.rotationPitch   = rot[1];
        mc.thePlayer.rotationYawHead = rot[0];
    }

    // ── rotation ────────────────────────────────────────────────────────────────

    private float[] getRotationsToTarget(EntityPlayer target) {
        boolean useBackup = ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue();
        Vec3 aimPoint = pickAimPoint(target, useBackup);
        if (aimPoint == null) return null;

        // Randomization varies the effective speed each tick by up to +-(randomization% * 50%).
        double jitter = (Math.random() * 2.0 - 1.0) * (randomization.getValue() / 100.0) * 0.5;
        double effectiveSpeed = MathHelper.clamp_double(speed.getValue() * (1.0 + jitter), 1.0, 30.0);
        float smoothFactor = 1.0f - (float) (effectiveSpeed / 30.0);

        return RotationUtil.getRotations(
                aimPoint.xCoord - mc.thePlayer.posX,
                aimPoint.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()),
                aimPoint.zCoord - mc.thePlayer.posZ,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 180.0f, smoothFactor);
    }

    /** Picks a point on the target, honoring the multipoint spread; falls back to head center. */
    private Vec3 pickAimPoint(EntityPlayer target, boolean useBackup) {
        AxisAlignedBB bb = target.getEntityBoundingBox();
        double mpH = multipointHorizontal.getValue() / 100.0;
        double mpV = multipointVertical.getValue() / 100.0;

        double width  = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double headY  = target.posY + target.getEyeHeight();

        double cx = (bb.minX + bb.maxX) / 2.0;
        double cz = (bb.minZ + bb.maxZ) / 2.0;

        Vec3 point = new Vec3(
                cx + (Math.random() - 0.5) * width * mpH,
                MathHelper.clamp_double(headY + (Math.random() - 0.5) * height * mpV, bb.minY + 0.1, bb.maxY - 0.1),
                cz + (Math.random() - 0.5) * width * mpH);
        if (!useBackup) return point;
        if (hasValidAimPoint(target, point)) return point;

        Vec3 fallback = new Vec3(cx, MathHelper.clamp_double(headY, bb.minY + 0.1, bb.maxY - 0.1), cz);
        return hasValidAimPoint(target, fallback) ? fallback : null;
    }

    private boolean hasValidAimPoint(EntityPlayer target, Vec3 point) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);

        if (ignoreBehindWalls.getValue()
                && mc.theWorld.rayTraceBlocks(eyes, point, false, true, false) != null) {
            return false;
        }

        if (ignoreBehindEntities.getValue()) {
            for (Object obj : mc.theWorld.playerEntities) {
                EntityPlayer other = (EntityPlayer) obj;
                if (other == target || other == mc.thePlayer) continue;
                AxisAlignedBB expanded = other.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
                if (expanded.calculateIntercept(eyes, point) != null) return false;
            }
        }
        return true;
    }

    // ── targeting ───────────────────────────────────────────────────────────────

    private EntityPlayer getEnemy() {
        int fovVal = (int) fov.getValue();

        List<EntityPlayer> candidates = new ArrayList<>();
        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (passesTargetFilters(p, fovVal)) candidates.add(p);
        }
        if (candidates.isEmpty()) return null;

        candidates.sort(getSortComparator().thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        if (ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue()) {
            for (EntityPlayer candidate : candidates) {
                if (pickAimPoint(candidate, true) != null) return candidate;
            }
            return null;
        }
        return candidates.get(0);
    }

    private boolean passesTargetFilters(EntityPlayer target, int fovVal) {
        if (target == mc.thePlayer || target.deathTime != 0) return false;
        if (TeamUtil.isFriend(target)) return false;
        if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(target)) return false;
        if (!aimInvis.getValue() && target.isInvisible()) return false;
        if (RotationUtil.distanceToBox(target.getEntityBoundingBox()) > range.getValue()) return false;
        if (TeamUtil.isBot(target)) return false;
        // angleToEntity is |yaw offset| * 2, so comparing to FOV means +-FOV/2 around the view.
        if (fovVal != 360 && RotationUtil.angleToEntity(target) > fovVal) return false;
        return true;
    }

    /** |yaw delta| + |pitch delta| from the current view to the target's body center. */
    private double angleDelta(EntityPlayer p) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        AxisAlignedBB bb = p.getEntityBoundingBox();
        double dx = (bb.minX + bb.maxX) / 2.0 - eyes.xCoord;
        double dy = (bb.minY + bb.maxY) / 2.0 - eyes.yCoord;
        double dz = (bb.minZ + bb.maxZ) / 2.0 - eyes.zCoord;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-Math.atan2(dy, flat) * 180.0 / Math.PI);
        return Math.abs(MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw))
             + Math.abs(MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch));
    }

    private Comparator<EntityPlayer> getSortComparator() {
        switch (sortMode.getIndex()) {
            case 0: return Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
            case 2: return Comparator.comparingInt(p -> p.hurtTime);
            case 3: return Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
            default: return Comparator.comparingDouble(this::angleDelta); // Angle
        }
    }

    // ── conditions ──────────────────────────────────────────────────────────────

    private boolean conditionsMet() {
        if (mc.currentScreen != null || !mc.inGameHasFocus) return false;
        if (weaponOnly.getValue() && !ItemUtil.isHoldingSword()) return false;
        if (clickAim.getValue() && !Mouse.isButtonDown(0)) return false;

        if (stopWhenBreaking.getValue() && isBreakingBlock()) {
            if (miningStartTime == -1L) miningStartTime = System.currentTimeMillis();
            if (System.currentTimeMillis() - miningStartTime >= (long) hoverDelay.getValue()) return false;
        } else {
            miningStartTime = -1L;
        }
        return true;
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && Mouse.isButtonDown(0);
    }
}
