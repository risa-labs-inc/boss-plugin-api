# BOSS Plugin API

Bundled system plugin for BOSS desktop application.

## Description

Core provider APIs for BOSS plugins. This system plugin provides the foundational APIs that other plugins depend on.

## Features

- System plugin (loads first with priority 0)
- Cannot be unloaded at runtime
- Provides plugin-to-plugin API registry
- Ships bundled with BossConsole

## Building

```bash
./gradlew buildPluginJar
```

The JAR will be generated at `build/libs/boss-plugin-api-1.0.14.jar`

## Installation

This plugin is bundled with BossConsole and does not need manual installation.

For development, copy the JAR to the BossConsole bundled-plugins directory:
```bash
cp build/libs/boss-plugin-api-*.jar ~/Development/BossConsole/composeApp/build/bundled-plugins/
```

## License

Proprietary - Risa Labs Inc.
