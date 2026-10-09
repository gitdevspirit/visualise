package myau.module.modules;

import myau.module.Module;
import myau.ui.clickgui.ModuleRegistry;
import myau.ui.clickgui.raven.RavenClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class GuiModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Cached so window positions and open modules survive closing the GUI. */
    private static RavenClickGui gui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);

        if (gui == null) {
            ModuleRegistry.init();
            gui = new RavenClickGui(
                    Arrays.asList("Render", "HUD"),
                    Arrays.asList(ModuleRegistry.renderModules, ModuleRegistry.hudModules)
            );
        }

        mc.displayGuiScreen(gui);
    }
}
