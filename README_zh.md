<h1 align="center">
  <img src="pd_logo.png" width="360" alt="Paper&#39;s Delight logo"><br>
  PapersDelight API
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Maven-dev.tako%3Apapersdelight--api%3A1.0.2-blue" alt="Maven 1.0.2">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Folia-1.21.x-3fb950" alt="Paper / Folia 1.21.x">
  <img src="https://img.shields.io/badge/Addon-API-5865F2" alt="附属插件 API">
  <img src="https://img.shields.io/badge/License-GPL%20v3-blue" alt="GPL-3.0">
</p>

<p align="center">
  <a href="README.md">English</a> | <b>简体中文</b>
</p>

<p align="center"><i>PapersDelight 的附属插件 API：菜单框架、配方类型、持续效果、伤害类型、CraftEngine 工具与 Folia 感知调度器。</i></p>

---

## 📖 这是什么

`PapersDelight-API` 是 **PapersDelight 插件与附属插件之间的契约**。它只包含接口、抽象基类和工具类——具体实现随 PapersDelight 插件 jar 一起分发，所以附属插件用 `compileOnly` 引入即可。

- 你按这个 artifact 写附属插件代码；
- 运行时由 PapersDelight 插件提供这些类（`join-classpath`）；
- 整个 `dev.tako.papersdelight.api` 包**豁免混淆**，保证跨版本链接稳定。

**我需要它吗？** 只有开发 PapersDelight 附属插件时才需要。服务器服主不需要单独安装它。

## 📦 里面有什么

| 包 | 作用 |
| --- | --- |
| `dev.tako.papersdelight.api` | `PapersDelightApi` —— API 版本契约（`VERSION`、`isCompatible(int)`） |
| `…api.menu` | GUI 框架：`Menu`、`MenuItem`、`MenuModule`、`SimpleMenuModule`、`MenuManager`、`MenuEventHandler`、`ClickHandler` 与注解辅助 |
| `…api.recipe` | `RecipeTypeRegistry` / `RecipeTypeHandler` —— 注册你自己的配方类型（由 CraftEngine 配置 section 解析） |
| `…api.effect` | `TimedEffectManager` —— 带 BossBar 计时与 PDC 持久化的持续效果基类 |
| `…api.damage` | `DamageTypes` / `DamageTypeDefinition` —— 在 bootstrap 阶段注册自定义伤害类型 |
| `…api.ce` | `CraftEngineUtil` —— 读写 CustomBlockState、物品 ID、创建 CE 物品、放置 CE 方块、访问包管理器 |
| `…api.config` | `GenerationAwareIdSectionConfigParser`、`ParserGeneration` —— 可重载安全的 CraftEngine 配置 parser 基类 |
| `…api.item` | 物品匹配器与高级标签门（`AdvancedTagGate`），用于把附属插件物品暴露给 PapersDelight 机制 |
| `…api.heat`、`…api.cold`、`…api.protection` | 门（Gate）：让附属插件告诉 PapersDelight 什么算热源/冷源，或某次交互是否受保护 |
| `…api.util` | `PaperScheduler`（Folia 感知任务封装 + `TaskHandle`）、`TextUtil`、`ParticleVisibility`、`ParticleThrottle` |

## 🚀 快速开始

**仓库地址**

```kotlin
repositories {
    maven("https://mvn.hezhongkj.top/releases/")
}
```

**Gradle（Kotlin DSL）**

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("dev.tako:papersdelight-api:1.0.2")
}
```

**Gradle（Groovy DSL）**

```groovy
dependencies {
    compileOnly 'dev.tako:papersdelight-api:1.0.2'
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
    <version>1.0.2</version>
    <scope>provided</scope>
</dependency>
```

> ℹ️ 这个 artifact **刻意不声明任何传递依赖**：请继续使用你自己的 Paper/Folia 编译目标（如果代码要用 CraftEngine 类型，自行添加 CE 依赖）。

### 1. 声明对 PapersDelight 的依赖

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

### 2. 启动时做兼容性检查

```java
@Override
public void onEnable() {
    if (!PapersDelightApi.isCompatible(PapersDelightApi.VERSION)) {
        getLogger().warning("PapersDelight API 版本不匹配，禁用本附属插件。");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
    // 之后可以安全调用 API
}
```

## 🧪 示例附属插件

[`example/`](example/) 里有一个完整可构建的示例：注册一个 GUI 模块、施加自定义持续效果、并使用 Folia 感知调度器。

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

构建并试用：

```bash
cd example
../gradlew build       # 产物：example/build/libs/ExampleAddon-1.0.0.jar
```

## 🔢 版本与兼容性

- API 与插件本体**独立版本号**（这里是 `1.0.x`，插件是 `PapersDelight 1.2.0`）。
- 次版本号只做**新增**；兼容区间由 `PapersDelightApi.MINIMUM_COMPATIBLE_VERSION` ~ `PapersDelightApi.VERSION` 表达。
- 启动时请用 `PapersDelightApi.isCompatible(你编译时的版本)` 判断，而不是硬编码版本号。
- `dev.tako.papersdelight.api.*` 豁免混淆：类名与方法名都属于对外契约。

## 🙏 致谢

- **作者：** Shimamura Tako、Mr Dg32z_、gukuan、Cold Leaves
- **技术支持：** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine)、[Paper](https://github.com/PaperMC/Paper)、[Folia](https://github.com/PaperMC/Folia)

## 📄 授权

本项目采用 **GNU General Public License v3.0**（GPL-3.0）——全文见 [LICENSE](LICENSE)。

PapersDelight 插件本体单独分发；本仓库只包含面向附属插件的 API。
