package myau.mixin;

import myau.Myau;
import myau.module.modules.Capes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {AbstractClientPlayer.class}, priority = 9991)
public abstract class MixinAbstractClientPlayer {
    /** Swap in the Capes module's texture for the local player only. */
    @Inject(
            method = {"getLocationCape()Lnet/minecraft/util/ResourceLocation;"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void visualise$getLocationCape(CallbackInfoReturnable<ResourceLocation> callbackInfoReturnable) {
        if (Myau.moduleManager == null) {
            return;
        }
        if ((Object) this != Minecraft.getMinecraft().thePlayer) {
            return;
        }
        Capes capes = (Capes) Myau.moduleManager.modules.get(Capes.class);
        if (capes != null && capes.isEnabled()) {
            callbackInfoReturnable.setReturnValue(capes.getCapeLocation());
        }
    }
}
