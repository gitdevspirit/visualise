package myau.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Entity.class})
public interface IAccessorEntity {
    @Accessor
    boolean getIsInWeb();

    @Invoker
    Vec3 callGetVectorForRotation(float float1, float float2);
}
