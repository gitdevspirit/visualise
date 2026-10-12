package myau.module.modules;

import myau.module.DropdownSetting;
import myau.module.Module;
import net.minecraft.util.ResourceLocation;

/**
 * Client-side capes. Only the local player sees the cape; other players are untouched.
 * The actual swap happens in MixinAbstractClientPlayer, which asks {@link #getCapeLocation()}.
 */
public class Capes extends Module {
    // New entries are appended so existing saved configs keep pointing at the same cape.
    private static final String[] NAMES = {
            "Visualise", "Visualise Dark", "Visualise Neon",
            "Visualise Lite", "Cat Eye", "Void Eye", "Sun Eye",
            "Lite Dark", "Lite Neon", "Lite Cat Eye", "Lite Void Eye", "Lite Sun Eye"
    };
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("client/capes/visualise.png"),
            new ResourceLocation("client/capes/visualise_dark.png"),
            new ResourceLocation("client/capes/visualise_neon.png"),
            new ResourceLocation("client/capes/visualise_lite.png"),
            new ResourceLocation("client/capes/cat_eye.png"),
            new ResourceLocation("client/capes/void_eye.png"),
            new ResourceLocation("client/capes/sun_eye.png"),
            new ResourceLocation("client/capes/visualise_lite_dark.png"),
            new ResourceLocation("client/capes/visualise_lite_neon.png"),
            new ResourceLocation("client/capes/cat_eye_lite.png"),
            new ResourceLocation("client/capes/void_eye_lite.png"),
            new ResourceLocation("client/capes/sun_eye_lite.png")
    };

    public final DropdownSetting cape = register(new DropdownSetting("Cape", 0, NAMES));

    public Capes() {
        super("Capes", false);
    }

    /** The texture for the currently selected cape. */
    public ResourceLocation getCapeLocation() {
        int index = this.cape.getIndex();
        if (index < 0 || index >= TEXTURES.length) {
            index = 0;
        }
        return TEXTURES[index];
    }
}
