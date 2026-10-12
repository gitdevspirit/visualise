package myau.module.modules;

import myau.module.DropdownSetting;
import myau.module.Module;
import net.minecraft.util.ResourceLocation;

/**
 * Client-side capes. Only the local player sees the cape; other players are untouched.
 * The actual swap happens in MixinAbstractClientPlayer, which asks {@link #getCapeLocation()}.
 *
 * The cape is picked from three independent options (click = next, right click = previous):
 * Design x Colour x Quality, which maps to client/capes/{design}_{colour}_{quality}.png.
 */
public class Capes extends Module {
    private static final String[] DESIGN_NAMES = {"V", "Eye", "Column", "Wordmark", "Cat Eye", "Void Eye", "Sun Eye"};
    private static final String[] DESIGN_IDS   = {"v", "eye", "column", "wordmark", "cat", "void", "sun"};

    private static final String[] COLOUR_NAMES = {"Purple", "Dark", "Neon", "Crimson", "Emerald", "Ice", "Gold", "Sunset"};
    private static final String[] COLOUR_IDS   = {"purple", "dark", "neon", "crimson", "emerald", "ice", "gold", "sunset"};

    /** Low = 128x64 (twice vanilla), HD = 512x256 (same layout, 8x). */
    private static final String[] QUALITY_NAMES = {"Low", "HD"};
    private static final String[] QUALITY_IDS   = {"low", "hd"};

    private static final ResourceLocation[][][] CACHE =
            new ResourceLocation[DESIGN_IDS.length][COLOUR_IDS.length][QUALITY_IDS.length];

    public final DropdownSetting design  = register(new DropdownSetting("Design", 0, DESIGN_NAMES));
    public final DropdownSetting colour  = register(new DropdownSetting("Colour", 0, COLOUR_NAMES));
    public final DropdownSetting quality = register(new DropdownSetting("Quality", 0, QUALITY_NAMES));

    public Capes() {
        super("Capes", false);
    }

    /** The texture for the currently selected design, colour and quality. */
    public ResourceLocation getCapeLocation() {
        int d = clamp(this.design.getIndex(), DESIGN_IDS.length);
        int c = clamp(this.colour.getIndex(), COLOUR_IDS.length);
        int q = clamp(this.quality.getIndex(), QUALITY_IDS.length);
        ResourceLocation location = CACHE[d][c][q];
        if (location == null) {
            location = new ResourceLocation("client/capes/" + DESIGN_IDS[d] + "_" + COLOUR_IDS[c] + "_" + QUALITY_IDS[q] + ".png");
            CACHE[d][c][q] = location;
        }
        return location;
    }

    private static int clamp(int index, int length) {
        return index < 0 || index >= length ? 0 : index;
    }
}
