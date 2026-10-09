package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

/**
 * Auto sprint: while you hold forward, the sprint key is held for you, so you never have to
 * double-tap W. Vanilla still applies its own rules (hunger, blindness, using an item, sneaking).
 */
public class Sprint extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public Sprint() {
        super("Sprint", true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        int sprintKey = mc.gameSettings.keyBindSprint.getKeyCode();
        boolean movingForward = mc.gameSettings.keyBindForward.isKeyDown();
        if (movingForward && !mc.thePlayer.isSneaking()) {
            KeyBindUtil.setKeyBindState(sprintKey, true);
        } else {
            KeyBindUtil.updateKeyState(sprintKey);   // hand control back to the real key
        }
    }

    @Override
    public void onDisabled() {
        if (mc.gameSettings != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
        }
    }
}
