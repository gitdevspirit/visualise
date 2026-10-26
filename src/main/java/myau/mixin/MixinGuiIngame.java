package myau.mixin;

import myau.event.EventManager;
import myau.events.Render2DEvent;
import myau.ui.hud.ArraylistHUD;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires Render2DEvent and draws the arraylist HUD at the end of the in-game overlay.
 * Replaces the old GuiIngameForge hooks: vanilla GuiIngame.renderGameOverlay is what Fabric has.
 * (Both the MCP and SRG names are listed so it resolves whichever the mappings expose.)
 */
@Mixin(value = {GuiIngame.class}, priority = 9999)
public abstract class MixinGuiIngame {
    private final ArraylistHUD visualise$hud = new ArraylistHUD();

    @Inject(method = {"renderGameOverlay", "func_175180_a"}, at = @At("TAIL"))
    private void visualise$renderOverlay(float partialTicks, CallbackInfo callbackInfo) {
        EventManager.call(new Render2DEvent(partialTicks));
        this.visualise$hud.render();
    }
}
