package myau.ui.clickgui.raven;

import myau.Myau;
import myau.module.modules.HUD;
import myau.ui.clickgui.GuiRender;

/** Layout constants, palette and the shared accent colour (follows the HUD colour mode). */
public final class Theme {

    private Theme() {}

    // ── Layout ──
    public static final int PANEL_W  = 108;
    public static final int HEADER_H = 19;
    public static final int BUTTON_H = 16;
    public static final int ROW_H    = 13;

    // ── Palette ──
    public static final int HEADER_BG  = 0xFF0B0B0B;
    public static final int BUTTON_BG  = 0xEB141414;
    public static final int BUTTON_HOV = 0xEB1D1D1D;
    public static final int ROW_BG     = 0xEB0E0E0E;
    public static final int TRACK_OFF  = 0xFF2B2B2B;

    public static final int TEXT_ON    = 0xFFFFFFFF;
    public static final int TEXT_OFF   = 0xFF7C7C7C;
    public static final int TEXT_ROW   = 0xFFD6D6D6;
    public static final int TEXT_DIM   = 0xFF8C8C8C;

    private static final int FALLBACK_ACCENT = 0xFF6FA8FF;

    /**
     * Accent colour at position t (0..1) along a gradient. Uses the HUD module's colour
     * settings, so the GUI always matches the arraylist.
     */
    public static int accent(float t) {
        try {
            HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
            if (hud != null) {
                int mode = hud.colorMode.getIndex();
                float span = mode == 2 ? 250f : mode == 1 ? 8f : 0f;
                return hud.getColor(System.currentTimeMillis(), t * span).getRGB() | 0xFF000000;
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_ACCENT;
    }

    public static int alpha(int color, int alpha) {
        return GuiRender.withAlpha(color, alpha);
    }

    public static float approach(float current, float target, float dt, float speed) {
        float next = current + (target - current) * Math.min(1f, dt * speed);
        return Math.abs(target - next) < 0.003f ? target : next;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
