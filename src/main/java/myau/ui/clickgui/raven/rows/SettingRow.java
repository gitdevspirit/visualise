package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;

import java.util.function.BooleanSupplier;

/** One line inside an expanded module. Subclasses draw themselves and handle their own input. */
public abstract class SettingRow {

    protected final String name;
    private final BooleanSupplier visible;
    protected float x, y, w;

    protected SettingRow(String name, BooleanSupplier visible) {
        this.name = name;
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }

    public int getHeight() {
        return Theme.ROW_H;
    }

    /** Width of whatever this row draws on its right-hand side (value text, switch, ...). */
    protected float rightWidth() {
        return 0f;
    }

    /** Narrowest panel width at which the label and the right-hand content do not overlap. */
    public float getMinWidth() {
        return 6f + GuiRender.textW(name) + 10f + rightWidth() + 6f;
    }

    /** Draws the row and remembers its bounds for hit testing. */
    public final void render(float x, float y, float w, int mx, int my, float dt, int accent) {
        this.x = x;
        this.y = y;
        this.w = w;
        draw(mx, my, dt, accent);
    }

    protected abstract void draw(int mx, int my, float dt, int accent);

    public boolean contains(int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my < y + getHeight();
    }

    /** Called only when the click is inside this row. Return true if handled. */
    public boolean mouseClicked(int mx, int my, int button) {
        return false;
    }

    public void mouseReleased() {
    }

    public boolean keyTyped(char c, int key) {
        return false;
    }

    public boolean isCapturing() {
        return false;
    }

    public void stopCapturing() {
    }

    /** Offered every click while this row is capturing keyboard input. True = consumed. */
    public boolean captureClick(int mx, int my, int button) {
        return false;
    }

    // ── helpers for subclasses ──

    protected void drawBase(int mx, int my) {
        GuiRender.rect(x, y, w, getHeight(), Theme.ROW_BG);
        if (contains(mx, my)) {
            GuiRender.rect(x, y, w, getHeight(), 0x12FFFFFF);
        }
    }

    protected float textY(float top, float height) {
        return top + (height - GuiRender.textH()) / 2f + 1f;
    }

    protected void drawLabel(int color) {
        GuiRender.textNoShadow(name, x + 6, textY(y, Theme.ROW_H), color);
    }

    protected void drawRight(String s, int color) {
        GuiRender.textNoShadow(s, x + w - 6 - GuiRender.textW(s), textY(y, Theme.ROW_H), color);
    }

    public static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-9) {
            return Long.toString((long) Math.rint(v));
        }
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
