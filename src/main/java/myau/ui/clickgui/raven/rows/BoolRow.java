package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** On/off option. Accent-filled switch + bright label when on, dark switch + dim label when off. */
public class BoolRow extends SettingRow {

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private float anim = -1f;

    public BoolRow(String name, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier visible) {
        super(name, visible);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void draw(int mx, int my, float dt, int accent) {
        boolean on = getter.getAsBoolean();
        if (anim < 0f) anim = on ? 1f : 0f;
        anim = Theme.approach(anim, on ? 1f : 0f, dt, 18f);

        drawBase(mx, my);
        if (anim > 0f) {
            GuiRender.rectGradientH(x, y, w, Theme.ROW_H, Theme.alpha(accent, (int) (0x30 * anim)), 0x00000000);
        }
        drawLabel(GuiRender.lerpColor(Theme.TEXT_OFF, Theme.TEXT_ON, anim));

        float sw = 16f, sh = 8f;
        float sx = x + w - 6 - sw;
        float sy = y + (Theme.ROW_H - sh) / 2f;
        GuiRender.roundedRect(sx, sy, sw, sh, sh / 2f, GuiRender.lerpColor(Theme.TRACK_OFF, accent, anim));
        float r = (sh - 3f) / 2f;
        float cx = sx + 1.5f + r + (sw - 3f - 2f * r) * anim;
        GuiRender.fillCircle(cx, sy + sh / 2f, r, GuiRender.lerpColor(0xFF7C7C7C, 0xFFFFFFFF, anim));
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            setter.accept(!getter.getAsBoolean());
            return true;
        }
        return false;
    }
}
