package myau.mixin;

import myau.event.EventManager;
import myau.events.MoveInputEvent;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {MovementInputFromOptions.class}, priority = 9999)
public abstract class MixinMovementInputFromOptions {
    @Inject(method = {"updatePlayerMoveState"}, at = {@At("RETURN")})
    private void onUpdatePlayerMoveState(CallbackInfo callbackInfo) {
        EventManager.call(new MoveInputEvent());
    }
}
