package myau.ui.clickgui.raven;

import myau.module.Module;
import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.rows.SettingRow;

import java.util.ArrayList;
import java.util.List;

/** A draggable window holding one category's modules. Right click the header to collapse. */
public class CategoryPanel {

    public final String name;
    public float x, y;
    public boolean collapsed;
    /** Current (animated) width; grows when a module's settings need more room. */
    public float w = Theme.PANEL_W;
    /** Position in the left-to-right default layout. */
    public final int slot;
    /** True once the user has dragged the window; it then stops following the auto layout. */
    public boolean userMoved;

    private final List<ModuleButton> buttons = new ArrayList<>();
    private boolean dragging;
    private float dragOffX, dragOffY;

    public CategoryPanel(String name, List<Module> modules, float x, float y, int slot) {
        this.name = name;
        this.slot = slot;
        this.x = x;
        this.y = y;
        for (Module m : modules) {
            if (m != null) buttons.add(new ModuleButton(m));
        }
    }

    public float getHeight() {
        float h = Theme.HEADER_H;
        if (!collapsed) {
            for (ModuleButton b : buttons) h += b.getHeight();
        }
        return h;
    }

    /** Width this panel wants right now: default, or wider if the header or an open module needs it. */
    public float getTargetWidth() {
        int enabled = 0;
        for (ModuleButton b : buttons) if (b.isOn()) enabled++;
        String count = enabled + "/" + buttons.size();
        float need = Math.max(Theme.PANEL_W, 7f + GuiRender.textW(name) + 12f + GuiRender.textW(count) + 7f);
        if (!collapsed) {
            for (ModuleButton b : buttons) need = Math.max(need, b.getMinWidth());
        }
        return (float) Math.ceil(need);
    }

    public void updateWidth(float dt) {
        w = Theme.approach(w, getTargetWidth(), dt, 14f);
    }

    public void draw(int mx, int my, float dt) {
        if (dragging) {
            float nx = mx - dragOffX, ny = my - dragOffY;
            if (nx != x || ny != y) userMoved = true;
            x = nx;
            y = ny;
        }

        float h = getHeight();
        GuiRender.rect(x - 1f, y - 1f, w + 2f, h + 2f, 0x58000000);
        GuiRender.rect(x, y, w, h, Theme.PANEL_BACKDROP);   // column background

        // header
        GuiRender.rect(x, y, w, Theme.HEADER_H, Theme.HEADER_BG);
        GuiRender.rectGradientH(x, y + Theme.HEADER_H - 1f, w, 1f, Theme.accent(0f), Theme.accent(1f));
        float ty = y + (Theme.HEADER_H - GuiRender.textH()) / 2f;
        GuiRender.text(name, x + 7f, ty, Theme.TEXT_ON);

        int enabled = 0;
        for (ModuleButton b : buttons) if (b.isOn()) enabled++;
        String count = enabled + "/" + buttons.size();
        GuiRender.textNoShadow(count, x + w - 7f - GuiRender.textW(count), ty + 1f,
                enabled > 0 ? Theme.accent(0.5f) : Theme.TEXT_DIM);

        if (collapsed) return;

        float cy = y + Theme.HEADER_H;
        int n = buttons.size();
        for (int i = 0; i < n; i++) {
            ModuleButton b = buttons.get(i);
            float t = n <= 1 ? 0f : (float) i / (n - 1);
            b.draw(x, cy, w, mx, my, dt, Theme.accent(t));
            cy += b.getHeight();
        }
    }

    /** True if the click hit this panel. */
    public boolean mouseClicked(int mx, int my, int button) {
        if (mx >= x && mx <= x + w && my >= y && my < y + Theme.HEADER_H) {
            if (button == 0) {
                dragging = true;
                dragOffX = mx - x;
                dragOffY = my - y;
            } else if (button == 1) {
                collapsed = !collapsed;
            }
            return true;
        }
        if (collapsed) return false;
        for (ModuleButton b : buttons) {
            if (b.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }

    public void mouseReleased() {
        dragging = false;
        for (ModuleButton b : buttons) b.mouseReleased();
    }

    public SettingRow findCapturing() {
        for (ModuleButton b : buttons) {
            SettingRow row = b.findCapturing();
            if (row != null) return row;
        }
        return null;
    }

    public void stopCapturing() {
        for (ModuleButton b : buttons) b.stopCapturing();
    }
}
