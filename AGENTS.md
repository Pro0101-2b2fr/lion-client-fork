# LionClient Development Guide

This is a Minecraft 1.8.9 mod project using ForgeGradle 2.1-SNAPSHOT.

## Development Environment
- **Java Version:** Required: **Java 8**. The project build will fail with newer Java versions. Use `JAVA_HOME` to point to a Java 8 JDK if necessary.
- **Build Tool:** Gradle 4.6 (via `gradlew`).

## Commands
- **Build:** `./gradlew build`
  - *Note:* If running on a system with a newer default Java, use: `JAVA_HOME=/path/to/java-8-jdk ./gradlew build`

## Project Quirks & Conventions
- **Tooling:** This repository uses a legacy ForgeGradle version. Do not attempt to upgrade Gradle/ForgeGradle without a full architectural review.
- **Settings:** Prefer `FloatSetting` for numeric values that require fractional precision (e.g., scale) to avoid integer truncation issues in `NumberSetting`.
- **Performance:**
  - Avoid creating new object instances (e.g., `new ItemStack[]`, `new ArrayList<>()`) inside `onRenderOverlay` or other high-frequency rendering methods. Reuse fields or cache objects instead.
  - Cache formatted strings (HP, distance, etc.) and update them on tick, not on render.
- **Mixins:** Mod functionality is injected using SpongePowered Mixins. Ensure new injections respect existing `mixins.lionclient.json` configuration.
