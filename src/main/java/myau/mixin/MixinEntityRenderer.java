package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.Render3DEvent;
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

    /**
     * Render3DEvent, replacing Forge's RenderWorldLastEvent. Forge dispatched it right before the "hand"
     * profiler section, i.e. after the world is drawn, with the camera matrices still active and before
     * the hand pass clears the depth buffer. Injecting before that section reproduces the same point.
     */
    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    args = "ldc=hand"
            )
    )
    private void visualise$renderWorldLast(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        EventManager.call(new Render3DEvent(partialTicks));
    }
}
