package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
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
 * AimAssist, rebuilt from the keystrokesmod reference.
 *
 * Smoothness: target selection runs once per game tick (20 Hz), but the camera is moved every
 * rendered frame. Each frame it chases the target's frame-interpolated position and closes a
 * frame-time-scaled fraction of the remaining angle, so motion is independent of the FPS and does
 * not step at 20 Hz. The turn is applied to both rotation and prevRotation exactly like a mouse
 * movement, so vanilla's camera interpolation doesn't fight it.
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

    // Chosen on the tick, consumed every frame.
    private EntityPlayer target;
    private boolean active;
    private double fx, fy, fz;          // aim-point offsets as fractions of the hitbox, re-rolled slowly
    private boolean centered;           // true = ignore offsets (used when only the center is visible)
    private int rerollTicks;
    private double speedJitter;         // per-tick Randomization noise, held steady across frames
    private long lastFrameNanos;

    public AimAssist() { super("AimAssist", false); }

    @Override
    public void onDisabled() {
        miningStartTime = -1L;
        target = null;
        active = false;
        lastFrameNanos = 0L;
    }

    // ── per tick: decide WHO to aim at ──────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) { target = null; active = false; return; }

        if (!conditionsMet()) { target = null; active = false; return; }

        EntityPlayer enemy = getEnemy();
        if (enemy == null) { target = null; active = false; return; }

        // Re-roll the multipoint offsets only now and then (and on a new target) so the aim point
        // drifts slowly instead of jumping every tick.
        if (enemy != target || --rerollTicks <= 0) {
            fx = Math.random() - 0.5;
            fy = Math.random() - 0.5;
            fz = Math.random() - 0.5;
            rerollTicks = 6 + (int) (Math.random() * 7);
        }
        // Randomization: speed noise, held for the whole tick so frames stay smooth.
        speedJitter = (Math.random() * 2.0 - 1.0) * (randomization.getValue() / 100.0) * 0.5;

        centered = false;
        boolean useBackup = ignoreBehindWalls.getValue() || ignoreBehindEntities.getValue();
        if (useBackup && !hasValidAimPoint(enemy, aimPoint(enemy, 1.0f))) {
            centered = true;
            if (!hasValidAimPoint(enemy, aimPoint(enemy, 1.0f))) { target = null; active = false; return; }
        }

        target = enemy;
        active = true;
    }

    // ── per frame: actually turn the camera ─────────────────────────────────────

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled()) return;

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 1.0 / 60.0 : (now - lastFrameNanos) / 1.0E9;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.05);                       // never lurch after a lag spike

        if (!active || target == null || mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null || !mc.inGameHasFocus) return;
        if (clickAim.getValue() && !Mouse.isButtonDown(0)) return;   // stop the moment you let go
        if (target.isDead || target.deathTime != 0) return;

        float pt = event.getPartialTicks();
        Vec3 eyes  = mc.thePlayer.getPositionEyes(pt);
        Vec3 point = aimPoint(target, pt);

        double dx = point.xCoord - eyes.xCoord;
        double dy = point.yCoord - eyes.yCoord;
        double dz = point.zCoord - eyes.zCoord;
        double flat = Math.sqrt(dx * dx + dz * dz);

        float wantYaw   = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float wantPitch = (float) (-Math.atan2(dy, flat) * 180.0 / Math.PI);

        float yawDelta   = MathHelper.wrapAngleTo180_float(wantYaw - mc.thePlayer.rotationYaw);
        float pitchDelta = wantPitch - mc.thePlayer.rotationPitch;

        // Speed 1..30 keeps its old meaning: fraction of the remaining angle closed per 20 Hz tick.
        // Converted to a per-frame fraction so the result is the same at any FPS.
        double eff = MathHelper.clamp_double(speed.getValue() * (1.0 + speedJitter), 1.0, 29.5);
        double perTick = eff / 30.0;
        float frac = (float) (1.0 - Math.pow(1.0 - perTick, dt * 20.0));

        float stepYaw   = Math.abs(yawDelta)   < 0.02f ? 0f : yawDelta   * frac;
        float stepPitch = Math.abs(pitchDelta) < 0.02f ? 0f : pitchDelta * frac;
        if (stepYaw == 0f && stepPitch == 0f) return;

        // Apply like a mouse movement (Entity#setAngles): move rotation AND prevRotation together.
        float oldYaw = mc.thePlayer.rotationYaw;
        float oldPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw   = oldYaw + stepYaw;
        mc.thePlayer.rotationPitch = RotationUtil.clampPitch(oldPitch + stepPitch);
        mc.thePlayer.prevRotationYaw   += mc.thePlayer.rotationYaw   - oldYaw;
        mc.thePlayer.prevRotationPitch += mc.thePlayer.rotationPitch - oldPitch;
        mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
    }

    // ── aim point ───────────────────────────────────────────────────────────────

    /**
     * The point to aim at, on the target's frame-interpolated position (so a target that only
     * updates at 20 Hz still moves smoothly), spread by the multipoint offsets.
     */
    private Vec3 aimPoint(EntityPlayer t, float partialTicks) {
        AxisAlignedBB bb = t.getEntityBoundingBox();
        double width  = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;

        double x = RenderUtil.lerpDouble(t.posX, t.lastTickPosX, partialTicks);
        double y = RenderUtil.lerpDouble(t.posY, t.lastTickPosY, partialTicks);
        double z = RenderUtil.lerpDouble(t.posZ, t.lastTickPosZ, partialTicks);

        double mpH = centered ? 0.0 : multipointHorizontal.getValue() / 100.0;
        double mpV = centered ? 0.0 : multipointVertical.getValue() / 100.0;

        double py = MathHelper.clamp_double(y + t.getEyeHeight() + fy * height * mpV, y + 0.1, y + height - 0.1);
        return new Vec3(x + fx * width * mpH, py, z + fz * width * mpH);
    }

    private boolean hasValidAimPoint(EntityPlayer t, Vec3 point) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);

        if (ignoreBehindWalls.getValue()
                && mc.theWorld.rayTraceBlocks(eyes, point, false, true, false) != null) {
            return false;
        }

        if (ignoreBehindEntities.getValue()) {
            for (Object obj : mc.theWorld.playerEntities) {
                EntityPlayer other = (EntityPlayer) obj;
                if (other == t || other == mc.thePlayer) continue;
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
            boolean wasCentered = centered;
            centered = true;
            try {
                for (EntityPlayer candidate : candidates) {
                    if (hasValidAimPoint(candidate, aimPoint(candidate, 1.0f))) return candidate;
                }
            } finally {
                centered = wasCentered;
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
