package myau.module.modules;

import myau.module.Module;
import myau.ui.clickgui.ModuleRegistry;
import myau.ui.clickgui.VapeClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class GuiModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        ModuleRegistry.init();

        VapeClickGui gui = new VapeClickGui(
            Arrays.asList("Render", "HUD"),
            Arrays.asList(
                ModuleRegistry.renderModules,
                ModuleRegistry.hudModules
            )
        );

        mc.displayGuiScreen(gui);
    }
}
