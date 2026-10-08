# Spirit Visuals

Visual-only extraction of the Spirit client for Forge 1.8.9. No combat, movement, anticheat,
commands or account manager - just the rendering features and the ClickGUI to control them.

## Modules

**Render:** ESP, Chams, Tracers, NameTags, BedESP, ItemESP, ChestESP, Xray, FullBright,
Trajectories, ViewClip, NoHurtCam

**HUD:** HUD (arraylist), InfoHUD, TargetHUD, Indicators, Radar, FPScounter, Notifications,
QualityOfLife (block overlay / keystrokes / CPS), ClickGui

Open the ClickGUI with **Right Shift**.

## Build

Requires JDK 8 for the toolchain (Gradle will fetch one if the foojay resolver can).

```bash
./gradlew build        # jar ends up in build/libs/
./gradlew runClient    # dev client
```

## Layout

- `myau/module`, `myau/property` - module + setting system
- `myau/module/modules` - the visual modules
- `myau/ui/clickgui` - ClickGUI (`VapeClickGui`, `GuiRender`, `RoundedUtils`, `ModuleRegistry`)
- `myau/ui/hud` - arraylist renderer
- `myau/util` (+ `render`, `shader`), `myau/font` - rendering helpers
- `myau/mixin` - only the render/event mixins
- `myau/Myau.java` - bootstrap; add new modules in `init()` and in `ModuleRegistry`

## Notes

- Config is saved to `./config/SpiritVisuals/` so it never collides with the full client's `./config/Myau/`.
- The Java package is still `myau`, so don't load this jar together with the full Spirit jar.
- Left out on purpose: BedwarsTag (needs the Hypixel Intel system), Dynamic Island overlay
  (unused and tied to Scaffold), custom main menu (tied to the account manager).
- Known gap: the ClickGUI only draws the newer `Setting` types. Modules using the older
  `Property` types (Xray, NoHurtCam) toggle fine but show no sliders/modes in the GUI yet.
