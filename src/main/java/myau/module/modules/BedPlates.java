package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorEntityRenderer;
import myau.mixin.IAccessorRenderManager;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.ui.clickgui.GuiRender;
import myau.util.KeyBindUtil;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Bed plates: floats the blocks that cover an enemy bed above it as a row of item icons
 * (with counts), nearest layer first. Ported from the Raven "BedPlates" script. Designed for
 * Bedwars, so the layer scan may not make sense in other game modes.
 *
 * Differences from the script: beds are found through the same chunk-render hook BedESP uses
 * (see MixinBlockRendererDispatcher) instead of scanning around players, and icons are keyed by
 * block + item damage rather than by name, so there is no block-name blacklist.
 */
public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int BACKGROUND = 0xFF25252B;
    private static final int BORDER = 0xFF34363B;
    private static final int MAX_LAYERS = 5;

    /** Head positions of beds seen while rendering chunks; filled by MixinBlockRendererDispatcher. */
    public final CopyOnWriteArraySet<BlockPos> beds = new CopyOnWriteArraySet<>();

    public final BooleanSetting alignTop = register(new BooleanSetting("Align Top Center", false));
    public final BooleanSetting autoScale = register(new BooleanSetting("Auto Scale", true));
    public final BooleanSetting showCounts = register(new BooleanSetting("Show Block Count", true));
    public final SliderSetting scale = register(new SliderSetting("Scale", 0.8, 0.1, 1.5, 0.1));
    public final SliderSetting renderDistance = register(new SliderSetting("Render Distance", 150, 10, 200, 1));
    public final SliderSetting yOffset = register(new SliderSetting("Y Offset", 1, -10, 10, 0.5));
    public final DropdownSetting mode = register(new DropdownSetting("Mode", 0, "STATIC", "TOGGLE", "HOLD"));
    public final KeybindSetting displayKey = register(new KeybindSetting("Display Key", 0));

    private final Map<BlockPos, BedData> data = new ConcurrentHashMap<>();
    private final Map<LayerKey, ItemStack> stacks = new HashMap<>();

    private boolean display = true;
    private boolean lastPressed;

    // projection buffers
    private final FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
    private final FloatBuffer screenOut = GLAllocation.createDirectFloatBuffer(3);

    public BedPlates() {
        super("BedPlates", false);
    }

    @Override
    public void onEnabled() {
        // beds are only reported while chunks are rebuilt, so rebuild to pick up the ones already loaded
        if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    }

    @Override
    public void onDisabled() {
        data.clear();
        beds.clear();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        data.clear();
        beds.clear();
        stacks.clear();
    }

    // ------------------------------------------------------------------------------------
    // data
    // ------------------------------------------------------------------------------------

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        long now = System.currentTimeMillis();

        for (BlockPos head : beds) {
            IBlockState state = mc.theWorld.getBlockState(head);
            if (!(state.getBlock() instanceof BlockBed) || state.getValue(BlockBed.PART) != BlockBed.EnumPartType.HEAD) {
                beds.remove(head);
                data.remove(head);
                continue;
            }
            if (data.containsKey(head)) continue;

            BlockPos foot = head.offset(state.getValue(BlockBed.FACING).getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(foot);
            if (!(footState.getBlock() instanceof BlockBed)) continue;

            // The layer maths wants position1 to be the piece with the larger coordinate.
            BlockPos p1 = head, p2 = foot;
            if (head.getX() < foot.getX() || head.getZ() < foot.getZ()) {
                p1 = foot;
                p2 = head;
            }
            BedData bed = new BedData(head, p1, p2);
            bed.distance = bed.lastDistance = distanceTo(p1);
            bed.layers = getBedDefenseLayers(p1, p2);
            bed.lastCheck = now;
            data.put(head, bed);
        }

        for (BedData bed : data.values()) {
            if (!beds.contains(bed.head)) {
                data.remove(bed.head);
                continue;
            }
            bed.lastDistance = bed.distance;
            bed.distance = distanceTo(bed.p1);
            bed.visible = mc.theWorld.getBlockState(bed.p1).getBlock() instanceof BlockBed;
            if (bed.visible && now > bed.lastCheck + getDelay(bed.distance)) {
                bed.layers = getBedDefenseLayers(bed.p1, bed.p2);
                bed.lastCheck = now;
            }
        }
    }

    private double distanceTo(BlockPos pos) {
        double dx = mc.thePlayer.posX - (pos.getX() + 0.5);
        double dy = mc.thePlayer.posY - pos.getY();
        double dz = mc.thePlayer.posZ - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int getDelay(double distance) {
        if (distance > 100) return 4000;
        if (distance > 50) return 3000;
        if (distance > 25) return 2000;
        return 1000;
    }

    /**
     * Walks outward from both halves of the bed, layer by layer (a diamond-shaped shell around the
     * bed), and tallies the blocks. A layer that is more than 20% air is skipped, two such layers
     * end the scan, and block types making up under 20% of a layer are ignored.
     */
    private Map<LayerKey, Integer> getBedDefenseLayers(BlockPos position1, BlockPos position2) {
        boolean facingZ = Math.abs(position2.getZ() - position1.getZ()) > Math.abs(position2.getX() - position1.getX());
        BlockPos[] bedParts = {position1, position2};

        Map<LayerKey, Integer> finalCounts = new LinkedHashMap<>();
        int airLayers = 0;

        for (int layer = 1; layer <= MAX_LAYERS; layer++) {
            Map<LayerKey, Integer> layerCounts = new LinkedHashMap<>();
            int total = 0;
            int air = 0;

            for (int part = 0; part < bedParts.length; part++) {
                BlockPos bed = bedParts[part];
                int offset = part == 0 ? layer : -layer;
                int startX = facingZ ? bed.getX() : bed.getX() + offset;
                int startY = bed.getY();
                int startZ = facingZ ? bed.getZ() + offset : bed.getZ();
                int dir = part == 0 ? 1 : -1;

                for (int step1 = 0; step1 <= layer; step1++) {
                    int yOffset = 0;
                    for (int step2 = step1; step2 >= 0; step2--) {
                        BlockPos pos1, pos2;
                        if (facingZ) {
                            pos1 = new BlockPos(startX - step2, startY + yOffset, startZ - dir * step1);
                            pos2 = new BlockPos(startX + step2, startY + yOffset, startZ - dir * step1);
                        } else {
                            pos1 = new BlockPos(startX - dir * step1, startY + yOffset, startZ - step2);
                            pos2 = new BlockPos(startX - dir * step1, startY + yOffset, startZ + step2);
                        }

                        if (countBlock(pos1, layerCounts)) air++;
                        total++;

                        if (!pos1.equals(pos2)) {
                            if (countBlock(pos2, layerCounts)) air++;
                            total++;
                        }

                        if (step2 > 0) yOffset++;
                    }
                }
            }

            if (total == 0 || (float) air / total > 0.2f) {
                if (++airLayers >= 2) break;
                continue;
            }

            for (Map.Entry<LayerKey, Integer> e : layerCounts.entrySet()) {
                if (!e.getKey().isAir() && (float) e.getValue() / total >= 0.2f) {
                    finalCounts.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }

        return finalCounts;
    }

    /** Adds the block at pos to the tally. Returns true if it is air. */
    private boolean countBlock(BlockPos pos, Map<LayerKey, Integer> counts) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        LayerKey key = new LayerKey(block, block.damageDropped(state));
        counts.merge(key, 1, Integer::sum);
        return key.isAir();
    }

    // ------------------------------------------------------------------------------------
    // rendering
    // ------------------------------------------------------------------------------------

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || data.isEmpty() || mc.thePlayer == null || mc.theWorld == null) return;

        if (mode.getIndex() != 0) {
            boolean noGui = mc.currentScreen == null;
            boolean pressed = displayKey.getKeyCode() != 0 && KeyBindUtil.isKeyDown(displayKey.getKeyCode());
            if (mode.getIndex() == 1) {
                if (pressed && !lastPressed) display = !display;
            } else {
                display = noGui && pressed;
            }
            lastPressed = pressed;
            if (!display) return;
        }

        float partialTicks = event.getPartialTicks();
        double maxDistance = renderDistance.getValue();

        List<BedData> sorted = new ArrayList<>(data.values());
        Collections.sort(sorted, Comparator.comparingDouble((BedData b) -> b.distance).reversed());

        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();

        GlStateManager.pushMatrix();
        for (BedData bed : sorted) {
            if (!bed.visible || bed.layers.isEmpty()) continue;

            double interpolated = bed.lastDistance + (bed.distance - bed.lastDistance) * partialTicks;
            if (interpolated > maxDistance) continue;

            double wx = (bed.p1.getX() + bed.p2.getX()) / 2.0 + 0.5;
            double wy = bed.p1.getY() + yOffset.getValue();
            double wz = (bed.p1.getZ() + bed.p2.getZ()) / 2.0 + 0.5;

            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
            float[] screen = project(wx - rm.getRenderPosX(), wy - rm.getRenderPosY(), wz - rm.getRenderPosZ(), scaleFactor);
            mc.entityRenderer.setupOverlayRendering();
            if (screen == null) continue;

            float s = autoScale.getValue()
                    ? (float) Math.max(0, scale.getValue() * (1 - interpolated / maxDistance))
                    : (float) scale.getValue();
            if (s <= 0.01f) continue;

            drawPlate(bed, screen[0], screen[1], s);
        }
        GlStateManager.popMatrix();

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** World (camera-relative) point to GUI-scaled screen coordinates, or null if behind the camera. */
    private float[] project(double x, double y, double z, int scaleFactor) {
        modelView.clear();
        projection.clear();
        viewport.clear();
        screenOut.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        if (!GLU.gluProject((float) x, (float) y, (float) z, modelView, projection, viewport, screenOut)) {
            return null;
        }
        float depth = screenOut.get(2);
        if (depth < 0f || depth >= 1f) return null;
        return new float[]{
                screenOut.get(0) / scaleFactor,
                (Display.getHeight() - screenOut.get(1)) / scaleFactor
        };
    }

    private void drawPlate(BedData bed, float sx, float sy, float s) {
        Map<LayerKey, Integer> layers = bed.layers;
        int n = layers.size();

        float itemSize = 16 * s;
        float pad = 2 * s;
        float box = itemSize + pad;
        float width = n * box;
        float height = box;

        float startX = sx - width / 2f;
        float startY = alignTop.getValue() ? sy : sy - height;
        float border = 3 * s;

        GlStateManager.disableDepth();
        GlStateManager.disableCull();

        GuiRender.roundedRect(startX - border, startY - border, width + border * 2, height + border * 2, border, BACKGROUND);

        int i = 0;
        for (Map.Entry<LayerKey, Integer> e : layers.entrySet()) {
            float ix = startX + i * box;
            float iy = startY;

            GuiRender.roundedRect(ix + pad / 4f, iy + pad / 4f, box - pad / 2f, box - pad / 2f, 2f * s, BORDER);

            GlStateManager.pushMatrix();
            GlStateManager.translate(ix + pad / 2f, iy + pad / 2f, 0f);
            GlStateManager.scale(s, s, 1f);
            GlStateManager.enableDepth();   // item models need the depth test (renderItemInGUI clears depth first)
            RenderUtil.renderItemInGUI(getStack(e.getKey()), 0, 0);
            GlStateManager.disableDepth();
            GlStateManager.popMatrix();

            if (showCounts.getValue() && e.getValue() > 1) {
                String text = String.valueOf(e.getValue());
                float ts = s * 0.6f;
                float tw = mc.fontRendererObj.getStringWidth(text) * ts;
                float tx = ix + box - pad / 2f - tw;
                float ty = iy + box - pad / 2f - mc.fontRendererObj.FONT_HEIGHT * ts;
                GlStateManager.pushMatrix();
                GlStateManager.translate(tx, ty, 0f);
                GlStateManager.scale(ts, ts, 1f);
                GlStateManager.enableTexture2D();
                mc.fontRendererObj.drawStringWithShadow(text, 0f, 0f, 0xFFFFFFFF);
                GlStateManager.popMatrix();
            }
            i++;
        }
    }

    private ItemStack getStack(LayerKey key) {
        ItemStack cached = stacks.get(key);
        if (cached != null) return cached;

        Block block = key.block;
        ItemStack stack;
        if (block instanceof BlockLiquid) {
            stack = new ItemStack(block.getMaterial() == Material.lava ? Items.lava_bucket : Items.water_bucket);
        } else if (block instanceof BlockFire) {
            stack = new ItemStack(Items.flint_and_steel);
        } else {
            Item item = Item.getItemFromBlock(block);
            stack = item == null ? new ItemStack(Blocks.barrier) : new ItemStack(item, 1, key.meta);
        }
        stacks.put(key, stack);
        return stack;
    }

    // ------------------------------------------------------------------------------------

    private static final class BedData {
        final BlockPos head;
        final BlockPos p1;
        final BlockPos p2;
        volatile double distance;
        volatile double lastDistance;
        volatile boolean visible = true;
        volatile long lastCheck;
        volatile Map<LayerKey, Integer> layers = Collections.emptyMap();

        BedData(BlockPos head, BlockPos p1, BlockPos p2) {
            this.head = head;
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    private static final class LayerKey {
        final Block block;
        final int meta;

        LayerKey(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }

        boolean isAir() {
            return block == Blocks.air;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LayerKey)) return false;
            LayerKey k = (LayerKey) o;
            return block == k.block && meta == k.meta;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(block) + meta;
        }
    }
}
