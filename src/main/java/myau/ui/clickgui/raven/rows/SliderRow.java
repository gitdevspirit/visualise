package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Number option: the row itself is the slider (accent fill shows the value). */
public class SliderRow extends SettingRow {

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min, max;
    private boolean dragging;
    private float shown = -1f;

    public SliderRow(String name, DoubleSupplier getter, DoubleConsumer setter,
                     double min, double max, BooleanSupplier visible) {
        super(name, visible);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
    }

    private void applyMouse(int mx) {
        float pct = Theme.clamp01((mx - (x + 4f)) / (w - 8f));
        setter.accept(min + (max - min) * pct);
    }

    @Override
    protected void draw(int mx, int my, float dt, int accent) {
        if (dragging) applyMouse(mx);
        double v = getter.getAsDouble();
        float pct = max > min ? Theme.clamp01((float) ((v - min) / (max - min))) : 0f;
        if (shown < 0f || dragging) shown = pct;
        shown = Theme.approach(shown, pct, dt, 22f);

        drawBase(mx, my);
        float fillW = w * shown;
        if (fillW > 0f) {
            GuiRender.rectGradientH(x, y, fillW, Theme.ROW_H, Theme.alpha(accent, 0x28), Theme.alpha(accent, 0x78));
            GuiRender.rect(x + fillW - 1f, y, 1f, Theme.ROW_H, accent);
        }
        drawLabel(Theme.TEXT_ROW);
        drawRight(format(v), Theme.TEXT_ON);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            dragging = true;
            applyMouse(mx);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased() {
        dragging = false;
    }
}
