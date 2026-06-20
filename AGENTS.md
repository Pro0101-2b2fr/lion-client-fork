# LionClient — Contributor & Agent Guide

LionClient is a **client-side-only Minecraft 1.8.9 Forge utility mod**. Features are
implemented as self-contained *modules* injected into the game through SpongePowered
Mixins and routed through a central event pipeline.

This document is the source of truth for how to build, navigate, and extend the
codebase. Read it before making changes. Keep it accurate when conventions change.

---

## 1. Toolchain

| Component        | Version / Value                          | Notes                                              |
| :--------------- | :--------------------------------------- | :------------------------------------------------- |
| Minecraft        | `1.8.9`                                  | `clientSideOnly = true`                            |
| Forge            | `1.8.9-11.15.1.2318-1.8.9`               | MCP mappings `stable_22`                           |
| ForgeGradle      | `2.1-SNAPSHOT`                           | **Legacy.** Do not upgrade without a full review.  |
| Gradle           | `4.6` (via `gradlew`)                    | Pinned in `gradle/wrapper/gradle-wrapper.properties` |
| **Java (JDK)**   | **8 — mandatory**                        | The build fails on newer JDKs.                     |
| Mixin            | `0.7.11-SNAPSHOT` (shaded)               | `compatibilityLevel: JAVA_8`                       |
| mixingradle      | `0.6-SNAPSHOT`                           |                                                    |
| Mod version      | `1.1.2`                                  | `modid = lionclient`                               |

> ⚠️ **Java 8 is non-negotiable.** The default system JDK is often newer. Always pin
> `JAVA_HOME` to a Java 8 JDK for any Gradle invocation.

---

## 2. Build & Run

```bash
# Standard build (pin Java 8)
JAVA_HOME=/path/to/java-8-jdk ./gradlew build

# Compile only (fast feedback loop)
JAVA_HOME=/path/to/java-8-jdk ./gradlew compileJava

# Set up the dev run client / IDE files
JAVA_HOME=/path/to/java-8-jdk ./gradlew setupDecompWorkspace
```

- On Debian/Ubuntu the JDK is typically at `/usr/lib/jvm/java-8-openjdk-amd64`.
- Use `--offline` once dependencies are cached to skip network resolution.
- The output jar is shaded with the Mixin runtime and lives in `build/libs/`.
- **Do not run the game client from non-interactive tooling.** Launch it manually.

### Build wiring worth knowing
- The `shade` configuration bundles the Mixin runtime into the final jar (`jar` task).
- The jar manifest registers the coremod (`FMLCorePlugin = com.lionclient.mixin.MixinLoader`),
  the `MixinTweaker`, and `MixinConfigs = mixins.lionclient.json`.
- `processResources` expands `version` / `mcversion` into `mcmod.info`.

---

## 3. Architecture

```
LionClient (@Mod entry point)
  ├── ModuleManager .............. registers every module, fans Forge events out to modules
  ├── PacketDelayManager ......... outbound packet scheduling (FakeLag, etc.)
  ├── KnockbackDelayBuffer ....... inbound packet buffering (KnockbackDelay)
  ├── ClickGuiScreen / ModernClickGuiScreen
  └── KeybindHandler ............. binds module toggles to keys

Mixins (com.lionclient.mixin)  ──fire──▶  EventBus (com.lionclient.event)  ──▶  modules
```

### Event flow — two complementary buses

1. **Forge events** (`@SubscribeEvent` in `LionClient`) are forwarded to
   `ModuleManager`, which calls the matching overridable hook on every *enabled*
   module: `onClientTick`, `onPlayerTick`, `onMouseEvent`, `onRenderOverlay`,
   `onRenderWorld`, `onPlayerJump`, packet hooks, etc. (see `Module`).

2. **Custom client events** (`com.lionclient.event.EventBus`) are fired from mixins
   for things Forge does not expose cleanly. Modules subscribe explicitly via
   `EventBus.getInstance().register(...)` in `onEnable` and **must** unregister in
   `onDisable`. Key events: `ClientRotationEvent` (silent aim), `PrePlayerInputEvent`
   (move-fix), `PrePlayerInteractEvent`, `StrafeEvent`, `JumpEvent`, `RunTickStartEvent`.

### Package map

| Package                         | Responsibility                                                        |
| :------------------------------ | :-------------------------------------------------------------------- |
| `com.lionclient`                | `@Mod` entry point, global event routing, friend lookup stub.         |
| `feature/module`                | `Module` base class, `Category`, `ModuleManager`.                     |
| `feature/module/impl`           | All concrete modules (~40). One file per feature.                     |
| `feature/setting`               | Setting types (see §5).                                               |
| `combat`                        | Shared aim math: `RotationState`, `KillAuraRotationUtils`, `TargetPredictor`, `ClientRotationHelper`. |
| `event`                         | Custom `EventBus` and event objects fired from mixins.                |
| `mixin` (+ `mixin/accessor`)    | All SpongePowered Mixins; `MixinLoader` is the coremod plugin.        |
| `network`                       | `PacketDelayManager`, `KnockbackDelayBuffer`.                         |
| `gui`                           | Classic + Modern ClickGUI, HUD editor, HUD elements.                  |
| `input`                         | `KeybindHandler`.                                                     |
| `util`                          | Helpers: `HumanClickTimer`, `MouseButtonHelper`, `MouseGcdHelper`, `EdgeDetectionHelper`. |

---

## 4. Modules

A module extends `com.lionclient.feature.module.Module`, declares its settings in the
constructor, and overrides only the lifecycle/event hooks it needs.

### Adding a module — checklist
1. Create the class in `feature/module/impl` extending `Module`; pass
   `(name, description, category, defaultKeyCode)` to `super(...)`.
2. Add settings as fields and register them with `addSetting(...)` in the constructor.
3. Register the instance in `ModuleManager`'s constructor (`register(new MyModule())`).
4. If the module subscribes to the custom `EventBus`, register in `onEnable` and
   **unregister in `onDisable`** — and reset all mutable state in both.
5. Verify with `compileJava`.

### Categories
`COMBAT`, `MOVEMENT`, `CLIENT`, `RENDER`, `PLAYER`, `MISC`, `HUD`.

### State-hygiene rules (these have caused real bugs)
- **Reset every mutable field** (counters, cached IDs, timers, `static` caches) in
  `onEnable`/`onDisable`. Leftover state leaks across toggles and world changes.
- **Read settings at use-time, not construction-time.** Do not snapshot a setting's
  value into a `final` field or pass it once to a long-lived helper — the in-game
  slider will then have no effect. (`RotationState` exposes runtime setters for exactly
  this reason; call them each tick.)

---

## 5. Settings

| Type             | Use for                                                              |
| :--------------- | :------------------------------------------------------------------- |
| `BooleanSetting` | On/off toggles.                                                      |
| `NumberSetting`  | **Integer-only** values. Truncates fractions — never use for scale/precision. |
| `DecimalSetting` | Fractional values with a range/step (CPS, range, speed).            |
| `FloatSetting`   | Fractional values needing fine precision (e.g. HUD scale).          |
| `IntRangeSetting`| A `[min, max]` integer pair (auto-orders low/high).                 |
| `EnumSetting<T>` | A choice among enum constants.                                       |
| `ColorSetting`   | ARGB color.                                                          |
| `ActionSetting`  | A clickable button (runs a `Runnable`).                              |

- **Fractional precision ⇒ `FloatSetting`/`DecimalSetting`, never `NumberSetting`.**
- `EnumSetting` is **persisted by `Enum.name()`** and **displayed via `toString()`**.
  If you want a friendly label (e.g. `"Tap Block"`), override `toString()` on the enum;
  do not rely on the constructor label being shown automatically.
- Settings auto-save: mutators call `ConfigManager.saveActiveConfig()`. Use the
  `*NoSave` variants when applying values *during* config load.
- Use `setVisibility(BooleanSupplier)` to conditionally hide settings in the GUI.

---

## 6. Performance (high-frequency render paths)

`onRenderOverlay`, `onRenderWorld`, and other per-frame methods run every frame. Follow
these rules to avoid frame-time churn and GC pressure:

- **Do not allocate per frame.** No `new ArrayList<>()`, `new ItemStack[]`,
  `new Comparator<>()`, `new Vec3(...)` in render methods. Reuse fields / cached buffers.
- **Compute on tick, render on frame.** Cache formatted strings (HP, distance, CPS, etc.)
  and rebuild them in a tick handler, not in the render call.
- Guard math against division by zero / `NaN` before it reaches the renderer.

---

## 7. Mixins

- Config: `src/main/resources/mixins.lionclient.json` (package `com.lionclient.mixin`,
  refmap `mixins.lionclient.refmap.json`, `compatibilityLevel JAVA_8`).
- **Every new mixin class must be added to the `client` array** in that JSON, or it will
  not be applied. Keep the file in sync with the package.
- Coremod entry point: `com.lionclient.mixin.MixinLoader` (declared in the jar manifest
  and `build.gradle` run args).
- Prefer firing a custom `EventBus` event from a thin mixin and putting logic in a module,
  rather than embedding feature logic directly in the mixin.
- Use `@Inject` with both the MCP name and the SRG name (e.g.
  `method = {"hurtCameraEffect", "func_78482_e"}`) so injections survive remapping.

---

## 8. Conventions & guardrails

- **Never upgrade Gradle / ForgeGradle / Forge** without a dedicated architectural review.
- Match the surrounding style: explicit types over `var`, no new dependencies, no new
  third-party libraries.
- Validate every change with `compileJava` (Java 8) before considering it done.
- This is a fork; the upstream is "completely made by AI" per `readme.MD`. Anti-cheat
  bypass is **not guaranteed** — do not assume any module is undetectable.
