package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

/**
 * While you hold attack on a block, switches to the best tool in your hotbar for it, and (optionally)
 * switches back to the slot you started on once you stop mining.
 */
public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot  = -1;
    private int previousSlot     = -1;
    private int tickDelayCounter = 0;

    public final SliderSetting  switchDelay = new SliderSetting("Delay", 0, 0, 5, 1);
    public final BooleanSetting switchBack  = new BooleanSetting("Switch Back", true);
    public final BooleanSetting sneakOnly   = new BooleanSetting("Sneak Only", true);

    public AutoTool() {
        super("AutoTool", false);
        register(switchDelay);
        register(switchBack);
        register(sneakOnly);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // The player changed slot themselves: forget what we were tracking.
        if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
            this.currentToolSlot = -1;
            this.previousSlot    = -1;
        }

        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem()) {
            if (this.tickDelayCounter >= (int) this.switchDelay.getValue()
                    && (!this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                int slot = ItemUtil.findInventorySlot(
                        mc.thePlayer.inventory.currentItem,
                        mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock());
                if (slot >= 0 && slot < 9 && mc.thePlayer.inventory.currentItem != slot) {
                    if (this.previousSlot == -1) this.previousSlot = mc.thePlayer.inventory.currentItem;
                    mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                }
            }
            this.tickDelayCounter++;
        } else {
            if (this.switchBack.getValue() && this.previousSlot != -1) {
                mc.thePlayer.inventory.currentItem = this.previousSlot;
            }
            this.currentToolSlot  = -1;
            this.previousSlot     = -1;
            this.tickDelayCounter = 0;
        }
    }

    @Override
    public void onDisabled() {
        this.currentToolSlot  = -1;
        this.previousSlot     = -1;
        this.tickDelayCounter = 0;
    }
}
