# Spirit Visuals

Visual-only extraction of the Spirit client for **Fabric 1.8.9 on [Ornithe](https://ornithemc.net)**. No combat, movement, anticheat,
commands or account manager - just the rendering features and the ClickGUI to control them.

## Modules

**Render:** ESP, Chams, Tracers, NameTags, BedESP, ItemESP, ChestESP, Xray, FullBright,
Trajectories, ViewClip, NoHurtCam, Capes (client-side Visualise cape, pick a colourway in the ClickGUI)

**HUD:** HUD (arraylist), InfoHUD, TargetHUD, Indicators, Radar, FPScounter, Notifications,
QualityOfLife (block overlay / keystrokes / CPS), ClickGui

Open the ClickGUI with **Right Shift**.

## Build

Needs JDK 21 to run Gradle (the mod itself compiles to Java 8 bytecode). The build uses Fabric Loom plus
[Ploceus](https://github.com/OrnitheMC/ploceus), Ornithe's Gradle plugin, which supplies Ornithe's Calamus intermediary.

The source keeps the MCP names it was written with. `tools/MakeMcpMappings.java` chains Ornithe's Calamus gen2
1.8.9 intermediary onto MCP (`mcp_stable` 22 + `joined.srg`) and writes `mappings/mcp-1.8.9.jar`, which
Loom uses as its "named" mappings. Generate it once (it downloads the data and caches it in `mappings/cache/`):

```bash
java tools/MakeMcpMappings.java      # -> mappings/mcp-1.8.9.jar
./gradlew build                      # jar ends up in build/libs/
./gradlew runClient                  # dev client
```

**Which intermediary?** The jar must be built against the intermediary the game runs on, otherwise every mixin
fails with "@Mixin target net.minecraft.unmapped.C_NNN was not found". The default is **Calamus gen2**, which is what
Ornithe / OneClient use for 1.8.9; `tools/data/calamus-intermediary-gen2-1.8.9.jar` is that file. (`--flavor legacy`
still builds mappings for a plain Legacy Fabric instance, but the Gradle build itself now targets Ornithe.)

If a download is blocked, fetch the files yourself and pass them in:
`java tools/MakeMcpMappings.java --intermediary <jar|tiny> --srg <mcp-1.8.9-srg.zip> --csv <mcp_stable-22-1.8.9.zip>`.

No Fabric API or Ornithe Standard Libraries are required.

If Gradle reports that Loom needs a newer Gradle, bump the wrapper with `./gradlew wrapper --gradle-version <version>`.

## Layout

- `myau/module`, `myau/property` - module + setting system
- `myau/module/modules` - the visual modules
- `myau/ui/clickgui` - ClickGUI (`raven/RavenClickGui`, `GuiRender`, `RoundedUtils`, `ModuleRegistry`)
- `myau/ui/hud` - arraylist renderer
- `myau/util` (+ `render`, `shader`), `myau/font` - rendering helpers
- `myau/mixin` - only the render/event mixins (`mixins.spiritvisuals.json` lists them; `fabric.mod.json` is the mod metadata)
- `tools/MakeMcpMappings.java` - builds the MCP-named mappings jar for Loom
- `myau/Myau.java` - bootstrap; add new modules in `init()` and in `ModuleRegistry`

## Notes

- Config is saved to `./config/SpiritVisuals/` so it never collides with the full client's `./config/Myau/`.
- The Java package is still `myau`, so don't load this jar together with the full Spirit jar.
- Ported from the Forge 1.8.9 build (last Forge commit: `247c5d2`). Forge's `RenderWorldLastEvent` and `GuiIngameForge`
  hooks are replaced by mixins into `EntityRenderer` and `GuiIngame`; `javax.vecmath` (a Forge-supplied library) is replaced by
  `myau.util.math`.
- Mixins are configured with `defaultRequire: 0` while the port is being verified, so a mixin target that does not resolve
  logs a warning instead of crashing. Check `latest.log` for `Injection warning` / `InjectionError` lines; once everything
  applies, remove the `injectors` block from `mixins.spiritvisuals.json` so a broken target fails loudly again.
- Left out on purpose: BedwarsTag (needs the Hypixel Intel system), Dynamic Island overlay
  (unused and tied to Scaffold), custom main menu (tied to the account manager).
- Known gap: the ClickGUI only draws the newer `Setting` types. Modules using the older
  `Property` types (Xray, NoHurtCam) toggle fine but show no sliders/modes in the GUI yet.
