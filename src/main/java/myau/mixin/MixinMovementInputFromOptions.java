package myau.mixin;

import myau.event.EventManager;
import myau.events.MoveInputEvent;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {MovementInputFromOptions.class}, priority = 9999)
public abstract class MixinMovementInputFromOptions {
    @Inject(method = {"updatePlayerMoveState"}, at = {@At("RETURN")})
    private void onUpdatePlayerMoveState(CallbackInfo callbackInfo) {
        EventManager.call(new MoveInputEvent());
    }
}
