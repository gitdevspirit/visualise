package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;
import org.lwjgl.input.Keyboard;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Free text option. Click to edit, Enter / Escape to finish. */
public class TextRow extends SettingRow {

    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private boolean editing;

    public TextRow(String name, Supplier<String> getter, Consumer<String> setter, BooleanSupplier visible) {
        super(name, visible);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void draw(int mx, int my, float dt, int accent) {
        drawBase(mx, my);
        drawLabel(Theme.TEXT_ROW);

        String value = getter.get();
        if (value == null) value = "";
        boolean caret = editing && (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = value + (caret ? "_" : "");
        float max = w - 12f - GuiRender.textW(name) - 6f;
        while (shown.length() > 0 && GuiRender.textW(shown) > max) {
            shown = shown.substring(1);
        }
        drawRight(shown, editing ? accent : Theme.TEXT_ON);
    }

    @Override
    protected float rightWidth() {
        String v = getter.get();
        return Math.min(70f, v == null ? 0f : GuiRender.textW(v + "_"));
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            editing = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char c, int key) {
        if (!editing) return false;
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
            editing = false;
            return true;
        }
        String value = getter.get() == null ? "" : getter.get();
        if (key == Keyboard.KEY_BACK) {
            if (!value.isEmpty()) setter.accept(value.substring(0, value.length() - 1));
        } else if (c >= 32 && c != 127) {
            setter.accept(value + c);
        }
        return true;
    }

    @Override
    public boolean captureClick(int mx, int my, int button) {
        return contains(mx, my);
    }

    @Override
    public boolean isCapturing() {
        return editing;
    }

    @Override
    public void stopCapturing() {
        editing = false;
    }
}
