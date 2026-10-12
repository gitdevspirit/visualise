package myau.ui.clickgui.raven.rows;

import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.Theme;
import myau.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Keybind. Click, then press a key or click any mouse button (left, right, middle, side). Escape clears the bind. */
public class BindRow extends SettingRow {

    private final IntSupplier getter;
    private final IntConsumer setter;
    private boolean listening;

    public BindRow(String name, IntSupplier getter, IntConsumer setter, BooleanSupplier visible) {
        super(name, visible);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void draw(int mx, int my, float dt, int accent) {
        drawBase(mx, my);
        drawLabel(Theme.TEXT_ROW);
        if (listening) {
            drawRight("Press key / click...", accent);
        } else {
            int key = getter.getAsInt();
            drawRight(key == 0 ? "None" : "[" + KeyBindUtil.getKeyName(key) + "]", key == 0 ? Theme.TEXT_DIM : Theme.TEXT_ON);
        }
    }

    @Override
    protected float rightWidth() {
        if (listening) return GuiRender.textW("Press key / click...");
        int key = getter.getAsInt();
        return GuiRender.textW(key == 0 ? "None" : "[" + KeyBindUtil.getKeyName(key) + "]");
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            listening = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char c, int key) {
        if (!listening) return false;
        setter.accept(key == Keyboard.KEY_ESCAPE ? 0 : key);
        listening = false;
        return true;
    }

    @Override
    public boolean captureClick(int mx, int my, int button) {
        if (!listening) return false;
        // Any mouse button binds, including left (LMB = -100) and right (RMB = -99).
        // Escape clears the bind instead.
        setter.accept(button - 100);
        listening = false;
        return true;
    }

    @Override
    public boolean isCapturing() {
        return listening;
    }

    @Override
    public void stopCapturing() {
        listening = false;
    }
}
