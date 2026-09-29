<h1 align="center">
  <img src="pd_logo.png" width="360" alt="Paper&#39;s Delight logo"><br>
  PapersDelight API
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Maven-dev.tako%3Apapersdelight--api%3A3.0.0-blue" alt="Maven 3.0.0">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Folia-1.21.x-3fb950" alt="Paper / Folia 1.21.x">
  <img src="https://img.shields.io/badge/Addon-API-5865F2" alt="Addon API">
  <img src="https://img.shields.io/badge/License-GPL%20v3-blue" alt="GPL-3.0">
</p>

<p align="center">
  <b>English</b> | <a href="README_zh.md">简体中文</a>
</p>

<p align="center"><i>The addon-facing API of PapersDelight: menus, recipe types, item matchers, damage-type contracts and capability gates — contracts only, no runtime implementations.</i></p>

---

## 📖 What is this?

`PapersDelight-API` is the **contract between the PapersDelight plugin and addon plugins**. It only contains interfaces, records and gates — the implementations ship inside the PapersDelight plugin jar, which is why addons link against this artifact with `compileOnly`.

- You write addon code against this artifact.
- The PapersDelight plugin provides the classes at runtime (`join-classpath`).
- The whole `dev.tako.papersdelight.api` package is **excluded from obfuscation**, so linkage stays stable across plugin releases.

**Do you need it?** Only if you develop an addon for PapersDelight. Server owners do not install this file.

## 📦 What is inside

| Package | Provides |
| --- | --- |
| `dev.tako.papersdelight.api` | `PapersDelightApi` — API version contract (`VERSION`, `isCompatible(int)`) |
| `…api.menu` | GUI contracts: `Menu`, `MenuItem`, `MenuModule`, `SimpleMenuModule`, `MenuEventHandler`, `ClickHandler`, annotation helpers — plus `MenuService`, the runtime handle the plugin publishes through the Bukkit service manager |
| `…api.recipe` | `RecipeTypeRegistry` / `RecipeTypeHandler` — register your own recipe types parsed from CraftEngine config sections |
| `…api.damage` | `DamageTypeDefinition` / `DamageTypeHandle` / `DamageTypeRegistrationState` — data contracts for custom damage types |
| `…api.config` | `GenerationAwareIdSectionConfigParser`, `ParserGeneration` — base classes for reload-safe CraftEngine config parsers |
| `…api.item` | Item matchers and the advanced-tag gate (`AdvancedTagGate`) used to expose addon items to PapersDelight mechanics |
| `…api.heat`, `…api.cold`, `…api.protection` | Gates that let addons tell PapersDelight what counts as a heat/cold source, or whether an interaction is protected |

Everything here is a **contract**: interfaces, records, builders and gates. The artifact deliberately contains **no menu engine, no effect engine, no CraftEngine helper and no scheduler** — the PapersDelight plugin owns every runtime implementation, so depending on this API never drags a third-party library into your addon.

## 🚀 Getting started

**Repository**

```kotlin
repositories {
    maven("https://mvn.hezhongkj.top/releases/")
}
```

**Gradle (Kotlin DSL)**

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("dev.tako:papersdelight-api:3.0.0")
}
```

**Gradle (Groovy DSL)**

```groovy
dependencies {
    compileOnly 'dev.tako:papersdelight-api:3.0.0'
}
```

**Maven**

```xml
<repository>
    <id>hezhong</id>
    <url>https://mvn.hezhongkj.top/releases/</url>
</repository>

<dependency>
    <groupId>dev.tako</groupId>
    <artifactId>papersdelight-api</artifactId>
    <version>3.0.0</version>
    <scope>provided</scope>
</dependency>
```

> ℹ️ The artifact declares **no dependencies at all**: `paper-api`, CraftEngine and PlaceholderAPI are compile-time only and are not published, so keep using your own Paper/Folia compile target (and add CraftEngine yourself if your code touches CE types).
>
> ℹ️ The plugin-owned GUI engine is reached through a service, not a class:
>
> ```java
> MenuService menus = MenuService.get();   // == Bukkit.getServicesManager().load(MenuService.class)
> if (menus != null) menus.openMenu(player, "my_module");
> ```

### 1. Depend on PapersDelight

```yaml
# paper-plugin.yml
name: MyAddon
version: '1.0.0'
main: com.example.myaddon.MyAddon
api-version: '1.21'

dependencies:
  server:
    PapersDelight:
      load: BEFORE
      required: true
      join-classpath: true
```

### 2. Check API compatibility on startup

```java
@Override
public void onEnable() {
    if (!PapersDelightApi.isCompatible(PapersDelightApi.VERSION)) {
        getLogger().warning("PapersDelight API version mismatch, disabling addon.");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
    // safe to use the API now
}
```

## 🧪 Example addon

A complete, buildable addon lives in [`example/`](example/) — it registers a GUI module, runs its own Bukkit-scheduled effect and uses no API implementation:

```java
public final class ExampleAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!PapersDelightApi.isCompatible(PapersDelightApi.VERSION)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ExampleEffect effect = new ExampleEffect(this);
        getServer().getPluginManager().registerEvents(effect, this);

        MenuService.get().registerModule(new SimpleMenuModule.Builder()
                .id("example")
                .menu(ExampleMenu::create)
                .handler(new ExampleMenuHandler(effect))
                .build());
    }
}
```

Build and try it:

```bash
cd example
../gradlew build       # produces example/build/libs/ExampleAddon-1.0.0.jar
```

## 🔢 Versioning & compatibility

- The API is versioned **independently** from the plugin (`3.x` here vs. `PapersDelight 1.2.x`).
- **3.0.0 is a breaking release**: every implementation left the API. `MenuManager`, `TimedEffectManager`, `EffectPdcStore`, `CraftEngineUtil`, `DamageTypes` and the old `api.util` helpers now live inside the PapersDelight plugin, and the API no longer depends on CC-Scheduler (the plugin still schedules with CC-Scheduler internally — that is its own business).
- Need the plugin’s GUI engine? It is published as a service: `MenuService.get()`; a `null` result means PapersDelight is missing or too old.
- Minor bumps only **add** members; the compatibility window is expressed by
  `PapersDelightApi.MINIMUM_COMPATIBLE_VERSION` … `PapersDelightApi.VERSION`.
- Always verify with `PapersDelightApi.isCompatible(yourCompiledVersion)` at startup instead of hard-coding versions.
- `dev.tako.papersdelight.api.*` is obfuscation-exempt: class and member names are part of the public contract.

## 🙏 Credits

- **Authors:** Shimamura Tako, Mr Dg32z_, gukuan, Cold Leaves
- **Powered by:** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine), [Paper](https://github.com/PaperMC/Paper), [Folia](https://github.com/PaperMC/Folia)

## 📄 License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for the full text.

The PapersDelight plugin itself is distributed separately; this repository only contains the addon-facing API.
