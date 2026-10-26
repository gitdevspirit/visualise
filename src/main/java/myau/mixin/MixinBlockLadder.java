package myau.mixin;

import myau.Myau;
import myau.module.modules.Xray;
import net.minecraft.block.BlockLadder;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {BlockLadder.class}, priority = 9999)
public abstract class MixinBlockLadder {
    @Inject(
            method = {"getBlockLayer"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void getBlockLayer(CallbackInfoReturnable<EnumWorldBlockLayer> callbackInfoReturnable) {
        if (Myau.moduleManager != null) {
            if (Myau.moduleManager.modules.get(Xray.class).isEnabled()) {
                callbackInfoReturnable.setReturnValue(EnumWorldBlockLayer.TRANSLUCENT);
            }
        }
    }
}
