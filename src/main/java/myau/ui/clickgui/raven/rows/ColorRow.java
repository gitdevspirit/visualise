package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** RGB colour picked with hue / saturation / brightness bars. */
public class ColorRow extends SettingRow {

    private static final int BAR_H = 4, GAP = 3, TOP = 14;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private float hue, sat, bri;
    private int lastSet = Integer.MIN_VALUE;
    private int dragBar = -1;

    public ColorRow(String name, IntSupplier getter, IntConsumer setter, BooleanSupplier visible) {
        super(name, visible);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public int getHeight() {
        return TOP + 3 * (BAR_H + GAP);
    }

    private void sync() {
        int rgb = getter.getAsInt() & 0xFFFFFF;
        if (dragBar == -1 && rgb != lastSet) {
            float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            if (hsb[1] > 0.001f && hsb[2] > 0.001f) hue = hsb[0];
            sat = hsb[1];
            bri = hsb[2];
            lastSet = rgb;
        }
    }

    private void push() {
        int rgb = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
        lastSet = rgb;
        setter.accept(rgb);
    }

    private float barY(int i) {
        return y + TOP + i * (BAR_H + GAP);
    }

    private void applyMouse(int mx) {
        float pct = Theme.clamp01((mx - (x + 6f)) / (w - 12f));
        if (dragBar == 0) hue = pct;
        else if (dragBar == 1) sat = pct;
        else if (dragBar == 2) bri = pct;
        push();
    }

    @Override
    protected void draw(int mx, int my, float dt, int accent) {
        if (dragBar != -1) applyMouse(mx);
        sync();

        GuiRender.rect(x, y, w, getHeight(), Theme.ROW_BG);
        drawLabel(Theme.TEXT_ROW);
        int current = 0xFF000000 | (getter.getAsInt() & 0xFFFFFF);
        GuiRender.roundedRect(x + w - 6 - 14, y + 3, 14, 8, 2f, current);

        float bx = x + 6f, bw = w - 12f;
        // hue
        float seg = bw / 6f;
        for (int i = 0; i < 6; i++) {
            int c1 = 0xFF000000 | (Color.HSBtoRGB(i / 6f, 1f, 1f) & 0xFFFFFF);
            int c2 = 0xFF000000 | (Color.HSBtoRGB((i + 1) / 6f, 1f, 1f) & 0xFFFFFF);
            GuiRender.rectGradientH(bx + seg * i, barY(0), seg + 0.5f, BAR_H, c1, c2);
        }
        int pure = 0xFF000000 | (Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF);
        int full = 0xFF000000 | (Color.HSBtoRGB(hue, sat, 1f) & 0xFFFFFF);
        GuiRender.rectGradientH(bx, barY(1), bw, BAR_H, 0xFFFFFFFF, pure);
        GuiRender.rectGradientH(bx, barY(2), bw, BAR_H, 0xFF000000, full);

        marker(bx + bw * hue, barY(0));
        marker(bx + bw * sat, barY(1));
        marker(bx + bw * bri, barY(2));
    }

    private void marker(float mx, float by) {
        GuiRender.rect(mx - 1f, by - 1f, 2f, BAR_H + 2f, 0xFFFFFFFF);
    }

    @Override
    protected float rightWidth() {
        return 14f;
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0) return false;
        for (int i = 0; i < 3; i++) {
            float by = barY(i);
            if (my >= by - 2 && my <= by + BAR_H + 2) {
                dragBar = i;
                applyMouse(mx);
                return true;
            }
        }
        return true;
    }

    @Override
    public void mouseReleased() {
        dragBar = -1;
    }
}
