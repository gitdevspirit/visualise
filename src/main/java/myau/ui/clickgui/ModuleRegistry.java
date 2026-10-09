package myau.ui.clickgui;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuleRegistry {

    public static List<Module> combatModules;
    public static List<Module> renderModules;
    public static List<Module> hudModules;
    public static List<Module> movementModules;

    public static void init() {
        Comparator<Module> byName = Comparator.comparing(m -> m.getName().toLowerCase());

        combatModules = new ArrayList<>();
        combatModules.add(Myau.moduleManager.getModule(AutoClicker.class));
        combatModules.sort(byName);

        renderModules = new ArrayList<>();
        renderModules.add(Myau.moduleManager.getModule(ESP.class));
        renderModules.add(Myau.moduleManager.getModule(Chams.class));
        renderModules.add(Myau.moduleManager.getModule(Tracers.class));
        renderModules.add(Myau.moduleManager.getModule(NameTags.class));
        renderModules.add(Myau.moduleManager.getModule(BedESP.class));
        renderModules.add(Myau.moduleManager.getModule(BedPlates.class));
        renderModules.add(Myau.moduleManager.getModule(ItemESP.class));
        renderModules.add(Myau.moduleManager.getModule(ChestESP.class));
        renderModules.add(Myau.moduleManager.getModule(Xray.class));
        renderModules.add(Myau.moduleManager.getModule(FullBright.class));
        renderModules.add(Myau.moduleManager.getModule(Trajectories.class));
        renderModules.add(Myau.moduleManager.getModule(ViewClip.class));
        renderModules.add(Myau.moduleManager.getModule(NoHurtCam.class));
        renderModules.sort(byName);

        movementModules = new ArrayList<>();
        movementModules.add(Myau.moduleManager.getModule(Sprint.class));
        movementModules.add(Myau.moduleManager.getModule(LegitScaffold.class));
        movementModules.sort(byName);

        hudModules = new ArrayList<>();
        hudModules.add(Myau.moduleManager.getModule(HUD.class));
        hudModules.add(Myau.moduleManager.getModule(InfoHUD.class));
        hudModules.add(Myau.moduleManager.getModule(TargetHUD.class));
        hudModules.add(Myau.moduleManager.getModule(Indicators.class));
        hudModules.add(Myau.moduleManager.getModule(Radar.class));
        hudModules.add(Myau.moduleManager.getModule(FPScounter.class));
        hudModules.add(Myau.moduleManager.getModule(Notifications.class));
        hudModules.add(Myau.moduleManager.getModule(QualityOfLife.class));
        hudModules.add(Myau.moduleManager.getModule(GuiModule.class));
        hudModules.sort(byName);
    }
}
