package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Pick-one option. Left click = next, right click = previous. */
public class ModeRow extends SettingRow {

    private final Supplier<String> getter;
    private final Runnable next, previous;

    public ModeRow(String name, Supplier<String> getter, Runnable next, Runnable previous, BooleanSupplier visible) {
        super(name, visible);
        this.getter = getter;
        this.next = next;
        this.previous = previous;
    }

    @Override
    protected void draw(int mx, int my, float dt, int accent) {
        drawBase(mx, my);
        drawLabel(Theme.TEXT_ROW);
        drawRight(getter.get(), accent);
    }

    @Override
    protected float rightWidth() {
        String v = getter.get();
        return v == null ? 0f : GuiRender.textW(v);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            next.run();
            return true;
        }
        if (button == 1) {
            previous.run();
            return true;
        }
        return false;
    }
}
