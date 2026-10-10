package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.RoundedUtils;
import myau.util.ItemUtil;
import myau.util.render.BlurShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class InfoHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DropdownSetting mode           = register(new DropdownSetting("Mode",           0, "Frosted", "Cards", "Pills", "Bars", "Minimal"));
    public final DropdownSetting posX           = register(new DropdownSetting("Position X",     0, "Left", "Center", "Right"));
    public final DropdownSetting posY           = register(new DropdownSetting("Position Y",     2, "Top",  "Center", "Bottom"));
    public final SliderSetting   offsetX        = register(new SliderSetting("X Offset",         0, -200, 200, 1));
    public final SliderSetting   offsetY        = register(new SliderSetting("Y Offset",         0, -200, 200, 1));
    public final SliderSetting   scale          = register(new SliderSetting("Scale",           1.0,  0.6, 2.0, 0.05));
    public final SliderSetting   blurStrength   = register(new SliderSetting("Blur Strength",     6,    1,  10,    1, () -> mode.getIndex() == 0));
    public final SliderSetting   cornerRadius   = register(new SliderSetting("Corner Radius",     6,    2,  20,    1, () -> mode.getIndex() != 4));
    public final SliderSetting   bgAlpha        = register(new SliderSetting("Background Alpha", 160,   0, 255,    1));
    public final BooleanSetting  showHealth     = register(new BooleanSetting("Show Health",   true));
    public final DropdownSetting healthMode     = register(new DropdownSetting("Health Mode",   0, "HEARTS", "TAB"));
    public final BooleanSetting  showBlocks     = register(new BooleanSetting("Show Blocks",   true));
    public final BooleanSetting  showHealthIcon = register(new BooleanSetting("Health Icon",   true));
    public final BooleanSetting  showBlockIcon  = register(new BooleanSetting("Block Icon",    true));

    public InfoHUD() { super("InfoHUD", false); }

    // ── Data helpers ──────────────────────────────────────────────────────────

    private String getHealthText() {
        if (healthMode.getIndex() == 1) {
            try {
                Scoreboard sb = mc.theWorld.getScoreboard();
                if (sb != null) {
                    ScoreObjective obj = sb.getObjectiveInDisplaySlot(2);
                    if (obj != null) {
                        Score score = sb.getValueFromObjective(mc.thePlayer.getName(), obj);
                        if (score != null) return String.valueOf(score.getScorePoints());
                    }
                }
            } catch (Exception ignored) {}
        }
        float hp = mc.thePlayer.getHealth();
        return hp == (int) hp ? String.valueOf((int)(hp / 2f)) : String.format("%.1f", hp / 2f);
    }

    private int getHealthColor() {
        float ratio = mc.thePlayer.getHealth() / mc.thePlayer.getMaxHealth();
        if (ratio > 0.6f) return 0xFFFF5555;
        if (ratio > 0.3f) return 0xFFFFAA00;
        return 0xFFFF2222;
    }

    private int getTotalBlocks() {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && ItemUtil.isBlock(stack)) total += stack.stackSize;
        }
        return total;
    }

    // ── Row model: the same stats, drawn differently by each mode ──────────────

    private static class Row {
        final String icon;    // "" when the icon setting is off
        final String label;
        final String value;
        final int    color;   // 0xAARRGGBB
        final float  ratio;   // 0..1, used by the Bars mode
        Row(String icon, String label, String value, int color, float ratio) {
            this.icon = icon; this.label = label; this.value = value; this.color = color; this.ratio = ratio;
        }
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        if (showHealth.getValue()) {
            float ratio = Math.max(0f, Math.min(1f, mc.thePlayer.getHealth() / mc.thePlayer.getMaxHealth()));
            rows.add(new Row(showHealthIcon.getValue() ? "\u2764 " : "", "Health",
                    getHealthText(), getHealthColor(), ratio));
        }
        if (showBlocks.getValue() && ItemUtil.isHoldingBlock()) {
            int blocks = getTotalBlocks();
            int col = blocks > 64 ? 0xFF55FF55 : blocks > 16 ? 0xFFFFAA00 : 0xFFFF5555;
            rows.add(new Row(showBlockIcon.getValue() ? "\u25A0 " : "", "Blocks",
                    String.valueOf(blocks), col, Math.min(1f, blocks / 256f)));
        }
        return rows;
    }

    // ── Small helpers ─────────────────────────────────────────────────────────

    private static int fw(String t) { return mc.fontRendererObj.getStringWidth(t); }
    private static int fh()         { return mc.fontRendererObj.FONT_HEIGHT; }

    private static int withAlpha(int argb, float a) {
        int alpha = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private int bgColor() {
        return ((int) bgAlpha.getValue() << 24) | 0x0E0E12;
    }

    private void beginText() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
    }

    private void text(String t, float x, float y, int color) {
        mc.fontRendererObj.drawStringWithShadow(t, x, y, color);
    }

    private void textCentered(String t, float x, float w, float y, int color) {
        text(t, x + (w - fw(t)) / 2f, y, color);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.currentScreen != null) return;

        List<Row> rows = buildRows();
        if (rows.isEmpty()) return;

        int m = mode.getIndex();
        float[] size;
        switch (m) {
            case 1:  size = sizeCards(rows);   break;
            case 2:  size = sizePills(rows);   break;
            case 3:  size = sizeBars(rows);    break;
            case 4:  size = sizeMinimal(rows); break;
            default: size = sizeFrosted(rows); break;
        }
        float panelW = size[0], panelH = size[1];

        ScaledResolution sr  = new ScaledResolution(mc);
        float scaleFactor    = (float) scale.getValue();
        float baseX, baseY;

        switch (posX.getIndex()) {
            case 0:  baseX = 10.0f; break;
            case 1:  baseX = sr.getScaledWidth()  / 2.0f - panelW * scaleFactor / 2.0f; break;
            default: baseX = sr.getScaledWidth()  - panelW * scaleFactor - 10.0f; break;
        }
        switch (posY.getIndex()) {
            case 0:  baseY = 10.0f; break;
            case 1:  baseY = sr.getScaledHeight() / 2.0f - panelH * scaleFactor / 2.0f; break;
            default: baseY = sr.getScaledHeight() - panelH * scaleFactor - 10.0f; break;
        }
        baseX += (float) offsetX.getValue();
        baseY += (float) offsetY.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, 1.0f);
        float dx = baseX / scaleFactor;
        float dy = baseY / scaleFactor;

        switch (m) {
            case 1:  drawCards(rows, dx, dy, panelH);                   break;
            case 2:  drawPills(rows, dx, dy, panelH);                   break;
            case 3:  drawBars(rows, dx, dy, panelW, panelH);            break;
            case 4:  drawMinimal(rows, dx, dy, panelW, panelH);         break;
            default: drawFrosted(rows, dx, dy, panelW, panelH);         break;
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    // ── Mode: Frosted (the original look) ─────────────────────────────────────

    private float[] sizeFrosted(List<Row> rows) {
        int lineH = fh() + 3, padding = 6, maxW = 0;
        for (Row r : rows) maxW = Math.max(maxW, fw(r.icon + r.value));
        return new float[]{ maxW + padding * 2, rows.size() * lineH + padding * 2 - 3 };
    }

    private void drawFrosted(List<Row> rows, float dx, float dy, float w, float h) {
        int lineH = fh() + 3, padding = 6;

        BlurShadowRenderer.renderFrostedGlass(
                dx, dy, w, h,
                (float) (int) cornerRadius.getValue(),
                (int) blurStrength.getValue(),
                (int) bgAlpha.getValue());

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = dy + padding + i * lineH;
            text(r.icon, dx + padding, ry, r.color);
            text(r.value, dx + w - padding - fw(r.value), ry, r.color);
        }
    }

    // ── Mode: Cards — one tile per stat, side by side ─────────────────────────
    // icon on top, big-ish value in the middle, label underneath, accent line at the bottom.

    private float cardW(Row r) {
        return Math.max(42f, Math.max(fw(r.value), fw(r.label)) + 16f);
    }

    private float[] sizeCards(List<Row> rows) {
        float w = 0f;
        for (Row r : rows) w += cardW(r);
        w += 4f * (rows.size() - 1);
        return new float[]{ w, fh() * 3 + 17f };
    }

    private void drawCards(List<Row> rows, float dx, float dy, float h) {
        float radius = (float) cornerRadius.getValue();
        float x = dx;
        for (Row r : rows) {
            float w = cardW(r);
            RoundedUtils.drawRoundedRect(x, dy, w, h, radius, bgColor());
            RoundedUtils.drawRoundedRect(x + 9f, dy + h - 5f, w - 18f, 2f, 1f, withAlpha(r.color, 0.9f));

            beginText();
            if (!r.icon.isEmpty()) textCentered(r.icon.trim(), x, w, dy + 6f, r.color);
            textCentered(r.value, x, w, dy + 6f + fh() + 3f, 0xFFFFFFFF);
            textCentered(r.label, x, w, dy + 6f + (fh() + 3f) * 2f, 0xFF8A8A99);
            x += w + 4f;
        }
    }

    // ── Mode: Pills — a capsule per stat in a row ─────────────────────────────

    private float pillW(Row r) {
        float left = r.icon.isEmpty() ? 9f : fw(r.icon.trim()) + 5f;
        return 8f + left + fw(r.value) + 9f;
    }

    private float[] sizePills(List<Row> rows) {
        float w = 0f;
        for (Row r : rows) w += pillW(r);
        w += 4f * (rows.size() - 1);
        return new float[]{ w, 17f };
    }

    private void drawPills(List<Row> rows, float dx, float dy, float h) {
        float x = dx;
        for (Row r : rows) {
            float w = pillW(r);
            RoundedUtils.drawRoundedRect(x, dy, w, h, h / 2f, bgColor());
            RoundedUtils.drawRoundedOutline(x, dy, w, h, h / 2f, 1f, withAlpha(r.color, 0.35f));

            float left;
            if (r.icon.isEmpty()) {
                GuiRender.fillCircle(x + 11f, dy + h / 2f, 5f, withAlpha(r.color, 0.2f));
                GuiRender.fillCircle(x + 11f, dy + h / 2f, 2.8f, r.color);
                left = 9f;
            } else {
                left = fw(r.icon.trim()) + 5f;
            }

            beginText();
            float ty = dy + (h - fh()) / 2f + 0.5f;
            if (!r.icon.isEmpty()) text(r.icon.trim(), x + 8f, ty, r.color);
            text(r.value, x + 8f + left, ty, r.icon.isEmpty() ? 0xFFFFFFFF : r.color);
            x += w + 4f;
        }
    }

    // ── Mode: Bars — label + value on top, a fill bar underneath ──────────────

    private float[] sizeBars(List<Row> rows) {
        int pad = 7, maxW = 0;
        for (Row r : rows) maxW = Math.max(maxW, fw(r.icon + r.label) + 14 + fw(r.value));
        float rowH = fh() + 11f;
        return new float[]{ Math.max(108f, maxW + pad * 2), rows.size() * rowH + pad * 2 - 5f };
    }

    private void drawBars(List<Row> rows, float dx, float dy, float w, float h) {
        int pad = 7;
        float rowH = fh() + 11f;
        float radius = (float) cornerRadius.getValue();

        RoundedUtils.drawRoundedRect(dx, dy, w, h, radius, bgColor());
        RoundedUtils.drawRoundedOutline(dx, dy, w, h, radius, 1f, 0x22FFFFFF);

        float inner = w - pad * 2f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = dy + pad + i * rowH;

            float by = ry + fh() + 3f;
            RoundedUtils.drawRoundedRect(dx + pad, by, inner, 3f, 1.5f, 0x33FFFFFF);
            float fill = Math.max(3f, inner * r.ratio);
            RoundedUtils.drawRoundedRect(dx + pad, by, fill, 3f, 1.5f, r.color);

            beginText();
            float lx = dx + pad;
            if (!r.icon.isEmpty()) { text(r.icon, lx, ry, r.color); lx += fw(r.icon); }
            text(r.label, lx, ry, 0xFF9A9AAA);
            text(r.value, dx + w - pad - fw(r.value), ry, r.color);
        }
    }

    // ── Mode: Minimal — no box, a soft fade behind colored accent ticks ───────

    private float[] sizeMinimal(List<Row> rows) {
        int maxW = 0;
        for (Row r : rows) maxW = Math.max(maxW, fw(r.icon + r.value));
        return new float[]{ 10f + maxW + 14f, rows.size() * (fh() + 4f) + 2f };
    }

    private void drawMinimal(List<Row> rows, float dx, float dy, float w, float h) {
        boolean right = posX.getIndex() == 2;
        int solid = withAlpha(0xFF0B0B0F, (float) bgAlpha.getValue() / 255f);
        int clear = withAlpha(0xFF0B0B0F, 0f);
        if (right) GuiRender.rectGradientH(dx, dy, w, h, clear, solid);
        else       GuiRender.rectGradientH(dx, dy, w, h, solid, clear);

        float lineH = fh() + 4f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = dy + 2f + i * lineH;
            float barX = right ? dx + w - 2f : dx;
            RoundedUtils.drawRoundedRect(barX, ry, 2f, fh() + 1f, 1f, r.color);

            beginText();
            float tx = dx + 8f;
            text(r.icon, tx, ry + 0.5f, r.color);
            text(r.value, tx + fw(r.icon), ry + 0.5f, r.color);
        }
    }
}
