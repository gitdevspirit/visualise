package myau.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({EntityRenderer.class})
public interface IAccessorEntityRenderer {
    @Invoker
    void callSetupCameraTransform(float float1, int integer);
}
