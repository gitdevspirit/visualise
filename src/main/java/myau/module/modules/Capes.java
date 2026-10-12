package myau.module.modules;

import myau.module.DropdownSetting;
import myau.module.Module;
import net.minecraft.util.ResourceLocation;

/**
 * Client-side capes. Only the local player sees the cape; other players are untouched.
 * The actual swap happens in MixinAbstractClientPlayer, which asks {@link #getCapeLocation()}.
 */
public class Capes extends Module {
    private static final String[] NAMES = {"Visualise", "Visualise Dark", "Visualise Neon"};
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("client/capes/visualise.png"),
            new ResourceLocation("client/capes/visualise_dark.png"),
            new ResourceLocation("client/capes/visualise_neon.png")
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
