# LionClient — Component Index

A navigation map of the codebase, kept current to help locate the right file fast
without re-scanning the tree. For build/conventions, see `AGENTS.md`.

> Paths are relative to `src/main/java/com/lionclient/` unless noted.

---

## Core / entry

| Concern                     | File                                              |
| :-------------------------- | :------------------------------------------------ |
| Mod entry, event routing    | `LionClient.java`                                 |
| Module registry & dispatch  | `feature/module/ModuleManager.java`               |
| Module base class           | `feature/module/Module.java`                      |
| Categories                  | `feature/module/Category.java`                    |
| Keybind → toggle binding    | `input/KeybindHandler.java`                       |
| Config load/save (per-profile) | `config/ConfigManager.java`                    |

## Event system

| Concern                      | File                                             |
| :--------------------------- | :----------------------------------------------- |
| Custom bus                   | `event/EventBus.java`, `event/IEventListener.java` |
| Silent-aim rotation hook     | `event/ClientRotationEvent.java`                 |
| Movement / input hooks       | `event/PrePlayerInputEvent.java`, `event/StrafeEvent.java`, `event/JumpEvent.java` |
| Interaction / tick hooks     | `event/PrePlayerInteractEvent.java`, `event/RunTickStartEvent.java` |

## Combat math (shared)

| Concern                          | File                                   |
| :------------------------------- | :------------------------------------- |
| Frame-rate-independent smoothing | `combat/RotationState.java` (runtime setters for speed/randomization) |
| Aim / raytrace utilities         | `combat/KillAuraRotationUtils.java`    |
| Latency prediction               | `combat/TargetPredictor.java`          |
| Server-rotation tracking         | `combat/ClientRotationHelper.java`     |

## Networking

| Concern                          | File                                   |
| :------------------------------- | :------------------------------------- |
| Outbound packet scheduling       | `network/PacketDelayManager.java`      |
| Inbound packet buffering         | `network/KnockbackDelayBuffer.java`    |

## GUI

| Concern                          | File                                   |
| :------------------------------- | :------------------------------------- |
| Classic ClickGUI                 | `gui/ClickGuiScreen.java`              |
| Modern ClickGUI (screen)         | `gui/ModernClickGuiScreen.java`        |
| Modern ClickGUI (layout/render)  | `gui/ModernClickGuiLayout.java`, `gui/ModernClickGuiRenderer.java`, `gui/ModernClickGuiBounds.java`, `gui/ModernClickGuiSnowflake.java` |
| HUD editor & draggable elements  | `gui/HudEditorScreen.java`, `gui/HudElement.java` |

## Settings (`feature/setting/`)

`Setting` (base) · `BooleanSetting` · `NumberSetting` (int) · `DecimalSetting` ·
`FloatSetting` · `IntRangeSetting` · `EnumSetting<T>` · `ColorSetting` · `ActionSetting`.
See `AGENTS.md` §5 for when to use each.

## Mixins (`mixin/`)

Coremod plugin `MixinLoader`. Config: `src/main/resources/mixins.lionclient.json`
(**add new mixin classes there**). Targets: `Minecraft`, `EntityRenderer`,
`EntityPlayerSP`, `EntityLivingBase`, `Entity`, `NetworkManager`,
`MovementInputFromOptions`, `GuiIngame`, `FontRenderer`, `RendererLivingEntity`,
`TileEntityRendererDispatcher`, plus `accessor/` invokers.

## Utilities (`util/`)

`HumanClickTimer` (human-like click delays) · `MouseButtonHelper` ·
`MouseGcdHelper` (GCD-correct rotation steps) · `EdgeDetectionHelper`.

---

## Modules by category (`feature/module/impl/`)

- **COMBAT** — `KillAuraModule`, `AimAssistModule`, `TriggerBotModule`, `ReachModule`,
  `AutoClickerModule`, `RightClickerModule`, `AutoBlockModule`, `WTapModule`,
  `JumpResetModule`, `KnockbackDelayModule`, `NoDelayModule`, `FakeLagModule`,
  `AntiBotModule`, `AntiFireballModule`, `AutoToolModule`, `ClutchModule` (+ `clutch/`),
  `BedBreakerModule` (+ `bedbreaker/`).
- **MOVEMENT** — `SprintModule`, `LegitScaffoldModule`.
- **RENDER** — `ChamsModule`, `PlayerEspModule`, `NameTagsModule`, `TrajectoriesModule`,
  `FullbrightModule`, `NoHurtCamModule`, `BedPlatesModule`.
- **HUD** — `HudModule`, `ArmorHudModule`, `KeystrokesModule`, `TargetHudModule`,
  `ClickPatternVisualizerModule`.
- **CLIENT / MISC / PLAYER** — `ClickGuiModule`, `ConfigModule`, `NameProtectModule`,
  `CleanScoreboardModule`, `ClickRecorderModule`, `RotationDebuggerModule`,
  `FakePlayerModule`.

Click-pipeline support (not standalone modules): `ClickEngine`, `ClickPatternStore`.

> When categories or module membership change, update this section.
