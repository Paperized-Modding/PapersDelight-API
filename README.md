<h1 align="center">
  <img src="pd_logo.png" width="110" alt="PapersDelight logo"><br>
  PapersDelight API
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Maven-dev.tako%3Apapersdelight--api%3A1.0.1-blue" alt="Maven 1.0.1">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Folia-1.21.x-3fb950" alt="Paper / Folia 1.21.x">
  <img src="https://img.shields.io/badge/Addon-API-5865F2" alt="Addon API">
</p>

<p align="center">
  <b>English</b> | <a href="README_zh.md">简体中文</a>
</p>

<p align="center"><i>The addon-facing API of PapersDelight: menus, recipe types, timed effects, damage types, CraftEngine helpers and Folia-safe schedulers.</i></p>

---

## 📖 What is this?

`PapersDelight-API` is the **contract between the PapersDelight plugin and addon plugins**. It only contains interfaces, base classes and utilities — the implementations ship inside the PapersDelight plugin jar, which is why addons link against this artifact with `compileOnly`.

- You write addon code against this artifact.
- The PapersDelight plugin provides the classes at runtime (`join-classpath`).
- The whole `dev.tako.papersdelight.api` package is **excluded from obfuscation**, so linkage stays stable across plugin releases.

**Do you need it?** Only if you develop an addon for PapersDelight. Server owners do not install this file.

## 📦 What is inside

| Package | Provides |
| --- | --- |
| `dev.tako.papersdelight.api` | `PapersDelightApi` — API version contract (`VERSION`, `isCompatible(int)`) |
| `…api.menu` | GUI framework: `Menu`, `MenuItem`, `MenuModule`, `SimpleMenuModule`, `MenuManager`, `MenuEventHandler`, `ClickHandler`, annotation helpers |
| `…api.recipe` | `RecipeTypeRegistry` / `RecipeTypeHandler` — register your own recipe types parsed from CraftEngine config sections |
| `…api.effect` | `TimedEffectManager` — timed potion-effect style mechanics with boss bar timer and PDC persistence |
| `…api.damage` | `DamageTypes` / `DamageTypeDefinition` — register custom damage types at bootstrap time |
| `…api.ce` | `CraftEngineUtil` — read/write CustomBlockState, item ids, create CE items, place CE blocks, access pack manager |
| `…api.config` | `GenerationAwareIdSectionConfigParser`, `ParserGeneration` — base classes for reload-safe CraftEngine config parsers |
| `…api.item` | Item matchers and the advanced-tag gate (`AdvancedTagGate`) used to expose addon items to PapersDelight mechanics |
| `…api.heat`, `…api.cold`, `…api.protection` | Gates that let addons tell PapersDelight what counts as a heat/cold source, or whether an interaction is protected |
| `…api.util` | `PaperScheduler` (Folia-aware task helpers + `TaskHandle`), `TextUtil`, `ParticleVisibility`, `ParticleThrottle` |

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
    compileOnly("dev.tako:papersdelight-api:1.0.1")
}
```

**Gradle (Groovy DSL)**

```groovy
dependencies {
    compileOnly 'dev.tako:papersdelight-api:1.0.1'
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
    <version>1.0.1</version>
    <scope>provided</scope>
</dependency>
```

> ℹ️ The artifact declares **no transitive dependencies** on purpose: keep using your own Paper/Folia compile target (and add CraftEngine yourself if your code touches CE types).

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

A complete, buildable addon lives in [`example/`](example/) — it registers a GUI module, applies a custom timed effect and uses the Folia-aware scheduler:

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

        MenuManager.getInstance().registerModule(new SimpleMenuModule.Builder()
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

- The API is versioned **independently** from the plugin (`1.0.x` here vs. `PapersDelight 1.2.0`).
- Minor bumps only **add** members; the compatibility window is expressed by
  `PapersDelightApi.MINIMUM_COMPATIBLE_VERSION` … `PapersDelightApi.VERSION`.
- Always verify with `PapersDelightApi.isCompatible(yourCompiledVersion)` at startup instead of hard-coding versions.
- `dev.tako.papersdelight.api.*` is obfuscation-exempt: class and member names are part of the public contract.

## 🛠 Building from source

Requires **JDK 21**.

```bash
./gradlew build                 # compiles and produces papersdelight-api-<version>.jar (+ sources)
./gradlew publishToMavenLocal   # installs into ~/.m2 for local addon testing
```

Publishing to the remote repository needs credentials in the Gradle user home (`gradle.properties`):

```properties
hezhongMavenUser=…
hezhongMavenPassword=…
```

then:

```bash
./gradlew publishMavenPublicationToHezhongReleasesRepository
```

## 🙏 Credits

- **Authors:** Shimamura Tako, Mr Dg32z_, gukuan, Cold Leaves
- **Powered by:** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine), [Paper](https://github.com/PaperMC/Paper), [Folia](https://github.com/PaperMC/Folia)

## 📄 License

This repository currently ships no `LICENSE` file — all rights are reserved by the authors unless stated otherwise. The API is published for addon development against the PapersDelight plugin.
