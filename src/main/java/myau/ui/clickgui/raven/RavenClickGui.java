package myau.ui.clickgui.raven;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.raven.rows.SettingRow;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Raven-style ClickGUI: floating category windows.
 *  - drag a header to move it, right click a header to collapse
 *  - left click a module to toggle it, right click to open its settings
 *  - mouse wheel scrolls every window
 */
public class RavenClickGui extends GuiScreen {

    private final List<CategoryPanel> panels = new ArrayList<>();

    private float openAnim;
    private long lastFrame;
    private float scrollTarget;
    private float scrollApplied;

    public RavenClickGui(List<String> categoryNames, List<List<Module>> categoryModules) {
        float x = 24f;
        for (int i = 0; i < categoryNames.size(); i++) {
            panels.add(new CategoryPanel(categoryNames.get(i), categoryModules.get(i), x, 24f));
            x += Theme.PANEL_W + 10f;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        openAnim = 0f;
        lastFrame = System.nanoTime();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (CategoryPanel p : panels) {
            p.stopCapturing();
            p.mouseReleased();
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1.0E9f);
        lastFrame = now;

        openAnim = Theme.approach(openAnim, 1f, dt, 12f);

        // scrolling
        float tallest = 0f;
        for (CategoryPanel p : panels) tallest = Math.max(tallest, p.getHeight());
        float minScroll = -Math.max(0f, tallest + 48f - height);
        scrollTarget = Math.max(minScroll, Math.min(0f, scrollTarget));
        float step = scrollTarget - scrollApplied;
        step = Math.abs(step) < 0.05f ? step : step * Math.min(1f, dt * 16f);
        if (step != 0f) {
            for (CategoryPanel p : panels) p.y += step;
            scrollApplied += step;
        }

        GuiRender.rect(0, 0, width, height, Theme.alpha(0x000000, (int) (0x70 * openAnim)));

        for (CategoryPanel p : panels) {
            p.draw(mouseX, mouseY, dt);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        SettingRow capturing = findCapturing();
        if (capturing != null) {
            if (capturing.captureClick(mouseX, mouseY, button)) return;
            for (CategoryPanel p : panels) p.stopCapturing();
        }

        for (int i = panels.size() - 1; i >= 0; i--) {
            CategoryPanel p = panels.get(i);
            if (p.mouseClicked(mouseX, mouseY, button)) {
                if (i != panels.size() - 1) {
                    panels.remove(i);
                    panels.add(p);
                }
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (CategoryPanel p : panels) p.mouseReleased();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && findCapturing() == null) {
            scrollTarget += wheel > 0 ? 22f : -22f;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        SettingRow capturing = findCapturing();
        if (capturing != null) {
            capturing.keyTyped(typedChar, keyCode);
            return;
        }

        Module gui = Myau.moduleManager.getModule(GuiModule.class);
        if (keyCode == Keyboard.KEY_ESCAPE || (gui != null && keyCode == gui.getKey())) {
            this.mc.displayGuiScreen(null);
        }
    }

    private SettingRow findCapturing() {
        for (CategoryPanel p : panels) {
            SettingRow row = p.findCapturing();
            if (row != null) return row;
        }
        return null;
    }
}
