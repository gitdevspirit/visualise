package myau.ui.clickgui.raven;

import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.rows.RowFactory;
import myau.ui.clickgui.raven.rows.SettingRow;

import java.util.List;

/**
 * One module line.
 * ON  = accent gradient fill, accent bar on the left, bright white text.
 * OFF = flat dark fill, dim grey text.
 * Left click toggles, right click expands the settings.
 */
public class ModuleButton {

    public final Module module;
    private final List<SettingRow> rows;

    private boolean open;
    private float openAnim;
    private float onAnim = -1f;
    private float hoverAnim;
    private float x, y, w;

    public ModuleButton(Module module) {
        this.module = module;
        this.rows = RowFactory.build(module);
    }

    public boolean isOn() {
        return module.isEnabled();
    }

    private float rowsHeight() {
        float h = 0f;
        for (SettingRow row : rows) {
            if (row.isVisible()) h += row.getHeight();
        }
        return h;
    }

    /** Narrowest width this button needs; includes its settings while they are open. */
    public float getMinWidth() {
        float need = 7f + GuiRender.textW(module.getName()) + 18f;
        if (open) {
            for (SettingRow row : rows) {
                if (row.isVisible()) need = Math.max(need, row.getMinWidth());
            }
        }
        return need;
    }

    public float getHeight() {
        return Theme.BUTTON_H + rowsHeight() * openAnim;
    }

    public void draw(float x, float y, float w, int mx, int my, float dt, int accent) {
        this.x = x;
        this.y = y;
        this.w = w;

        boolean on = module.isEnabled();
        if (onAnim < 0f) onAnim = on ? 1f : 0f;
        onAnim = Theme.approach(onAnim, on ? 1f : 0f, dt, 16f);
        openAnim = Theme.approach(openAnim, open ? 1f : 0f, dt, 14f);

        boolean hovered = mx >= x && mx <= x + w && my >= y && my < y + Theme.BUTTON_H;
        hoverAnim = Theme.approach(hoverAnim, hovered ? 1f : 0f, dt, 20f);

        // base
        GuiRender.rect(x, y, w, Theme.BUTTON_H, GuiRender.lerpColor(Theme.BUTTON_BG, Theme.BUTTON_HOV, hoverAnim));

        // ON state
        if (onAnim > 0f) {
            GuiRender.rectGradientH(x, y, w, Theme.BUTTON_H,
                    Theme.alpha(accent, (int) (0x8C * onAnim)),
                    Theme.alpha(accent, (int) (0x38 * onAnim)));
            GuiRender.rect(x, y, 2f, Theme.BUTTON_H, accent);
        }

        int textColor = GuiRender.lerpColor(Theme.TEXT_OFF, Theme.TEXT_ON, onAnim);
        float ty = y + (Theme.BUTTON_H - GuiRender.textH()) / 2f + 1f;
        GuiRender.text(module.getName(), x + 7f, ty, textColor);

        if (!rows.isEmpty()) {
            float tx = x + w - 9f, ty2 = y + Theme.BUTTON_H / 2f;
            int tc = open ? 0xFFFFFFFF : (on ? 0xCCFFFFFF : 0xFF5A5A5A);
            if (openAnim > 0.5f) {
                GuiRender.triangle(tx - 3f, ty2 - 1.5f, tx + 3f, ty2 - 1.5f, tx, ty2 + 2.5f, tc);
            } else {
                GuiRender.triangle(tx - 1.5f, ty2 - 3f, tx - 1.5f, ty2 + 3f, tx + 2.5f, ty2, tc);
            }
        }

        // settings
        if (openAnim > 0.001f) {
            float total = rowsHeight() * openAnim;
            float top = y + Theme.BUTTON_H;
            GuiRender.pushScissor(x, top, w, total);
            float ry = top;
            for (SettingRow row : rows) {
                if (!row.isVisible()) continue;
                row.render(x, ry, w, mx, my, dt, accent);
                ry += row.getHeight();
            }
            GuiRender.rect(x, top, 1f, rowsHeight(), Theme.alpha(accent, 0xB0));
            GuiRender.popScissor();
        }
    }

    /** True if the click landed on this button (button face or one of its rows). */
    public boolean mouseClicked(int mx, int my, int button) {
        if (mx < x || mx > x + w) return false;

        if (my >= y && my < y + Theme.BUTTON_H) {
            if (button == 0 && !(module instanceof GuiModule)) {
                module.toggle();
            } else if (button == 1 && !rows.isEmpty()) {
                open = !open;
            }
            return true;
        }

        if (open && openAnim > 0.98f && my >= y + Theme.BUTTON_H && my < y + getHeight()) {
            for (SettingRow row : rows) {
                if (row.isVisible() && row.contains(mx, my)) {
                    row.mouseClicked(mx, my, button);
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        for (SettingRow row : rows) row.mouseReleased();
    }

    public SettingRow findCapturing() {
        for (SettingRow row : rows) {
            if (row.isCapturing()) return row;
        }
        return null;
    }

    public void stopCapturing() {
        for (SettingRow row : rows) row.stopCapturing();
    }
}
