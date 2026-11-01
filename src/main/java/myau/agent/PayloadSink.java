package myau.agent;

import myau.Myau;
import myau.event.EventManager;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.LeftClickMouseEvent;
import myau.events.LoadWorldEvent;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.ResizeEvent;
import myau.events.TickEvent;
import myau.init.Initializer;
import myau.ui.hud.ArraylistHUD;
import net.minecraft.client.Minecraft;
import visualise.boot.Sink;

/**
 * Launch-injection counterpart of the old Mixin classes: the injector's hooks (visualise.boot.Hooks) land here
 * and are turned into the same events the modules already listen to.
 *
 * Ported: MixinMinecraft, the Render3DEvent part of MixinEntityRenderer, MixinGuiIngame.
 * Still on Mixin only (see injector/README.md): chams / xray / block + leaf rendering, NoHurtCam, movement, packets.
 */
public class PayloadSink implements Sink {
    private final ArraylistHUD hud = new ArraylistHUD();

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.theWorld != null && mc.thePlayer != null;
    }

    @Override public void startGameHead()   { new Initializer(); }
    @Override public void startGameReturn() { new Myau(); }

    @Override public void runTickHead()   { if (inWorld()) EventManager.call(new TickEvent(EventType.PRE)); }
    @Override public void runTickReturn() { if (inWorld()) EventManager.call(new TickEvent(EventType.POST)); }

    @Override public void loadWorld() { EventManager.call(new LoadWorldEvent()); }

    @Override
    public boolean clickMouse() {
        LeftClickMouseEvent event = new LeftClickMouseEvent();
        EventManager.call(event);
        return event.isCancelled();
    }

    @Override public void resize() { EventManager.call(new ResizeEvent()); }

    @Override public void render3D(float partialTicks) { EventManager.call(new Render3DEvent(partialTicks)); }

    @Override
    public void render2D(float partialTicks) {
        EventManager.call(new Render2DEvent(partialTicks));
        hud.render();
    }

    @Override
    public void keyState(int key, boolean state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (state && mc != null && mc.currentScreen == null) EventManager.call(new KeyEvent(key));
    }
}
