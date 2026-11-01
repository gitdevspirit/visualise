# Launch injector (Lunar Client / Badlion Client)

Loads Visualise into a client that does not run Fabric, using the JVM's own `-agentpath` mechanism, so it is
in the process before the game's classes exist. Nothing in the client is modified on disk.

```
JVM start ──> libvisualise (JVMTI agent, C)            native/visualise_agent.c
                 ├─ adds visualise-agent.jar to the bootstrap path (visible to every classloader)
                 ├─ class-file-load hook ──> Boot.transform ──> engine (ASM, private classloader)
                 └─ JNI DefineClass, exposed as Boot.define0
engine        ──> patches the game's classes (obfuscated names, via obf.map) to call visualise.boot.Hooks
first hook    ──> defines the payload (the normal myau.* code) in the game's own classloader,
                 renaming MCP -> the client's names and turning @Accessor interfaces into direct field access
```

| piece | where |
|---|---|
| native agent | `native/` (vendored `jni.h` / `jvmti.h`, so no JDK is needed on the Mac to compile) |
| bootstrap (`Boot`, `Hooks`, `Sink`) | `loader/` |
| engine (hooks, mapper, payload rewriter) | `engine/` |
| payload entry | `src/main/java/myau/agent/` |

## Build and install

```bash
injector/fetch-asm.sh && injector/build.sh        # agent + jars  (macOS: universal arm64+x86_64, ad-hoc signed)
java tools/MakeMcpMappings.java --obf-map mappings/obf-1.8.9.map
./gradlew payloadJar                               # build/injector/visualise-payload.jar
injector/install.sh                                # copies to ~/.visualise, prints the JVM argument
injector/doctor.sh                                 # does Lunar's / Badlion's java accept the agent? (read-only)
```

Paste the printed `-agentpath:...` argument into the launcher's JVM arguments once. On exit the game log lists
every hook as applied or `NOT APPLIED`; a missing one means that client's class/method doesn't match.

## Verified here, and not

`injector/test/run-test.sh` runs the whole chain against a fake game, once with MCP names and once renamed to
short obfuscated names with a generated `obf.map` (hooks, cancel, private-field accessors, inherited-method
overrides, payload defined in the game loader). It passes on Linux / JDK 21.

**Not verified:** anything on macOS, and anything against a real Lunar or Badlion. Those clients may restructure
`startGame` / `runTick`, ship a different JVM, or change obfuscated names between releases.
`tools/MakeMcpMappings.java --obf-map` has only been run on synthetic data (no network here), and
`./gradlew payloadJar` has not been run.

## Hook status (ported from `src/main/java/myau/mixin`)

Ported: `MixinMinecraft` (startGame, runTick, loadWorld, clickMouse, framebuffer resize, key state),
`MixinEntityRenderer` Render3D, `MixinGuiIngame` Render2D + HUD. The `IAccessor*` interfaces need no port.

Still Mixin-only, so those modules do nothing under the injector yet: NoHurtCam (`hurtCameraEffect`),
Chams/ESP outlines (`MixinRendererLivingEntity`), Xray and block/leaf/pane/web rendering (`MixinBlock*`,
`MixinWorldRenderer`, `MixinVisGraph`), `MixinItemStack`, `MixinMovementInputFromOptions`, `MixinNetworkManager`,
`MixinAbstractClientPlayer` (Capes). Cape textures are resources and are not loaded from the payload jar yet.

Add a hook: one line in `HookTransformer.defaultHooks()`, one method each in `Hooks`, `Sink` and `PayloadSink`.
