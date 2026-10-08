package myau.mixin;

import myau.Myau;
import myau.module.modules.NoHurtCam;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /** NoHurtCam: with the multiplier at 0% the hurt camera shake is skipped entirely. */
    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void onHurtCam(float ticks, CallbackInfo ci) {
        if (Myau.moduleManager == null) return;
        NoHurtCam noHurtCam = (NoHurtCam) Myau.moduleManager.modules.get(NoHurtCam.class);
        if (noHurtCam != null && noHurtCam.isEnabled() && noHurtCam.multiplier.getValue() == 0) {
            ci.cancel();
        }
    }
}
