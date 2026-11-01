package org.spongepowered.asm.mixin.gen;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD)
public @interface Accessor { String value() default ""; }
