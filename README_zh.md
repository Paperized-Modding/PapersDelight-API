<h1 align="center">
  <img src="pd_logo.png" width="360" alt="Paper&#39;s Delight logo"><br>
  PapersDelight API
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Maven-dev.tako%3Apapersdelight--api%3A4.0.0-blue" alt="Maven 4.0.0">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper%20%2F%20Folia-1.21.x-3fb950" alt="Paper / Folia 1.21.x">
  <img src="https://img.shields.io/badge/Addon-API-5865F2" alt="附属插件 API">
  <img src="https://img.shields.io/badge/License-GPL%20v3-blue" alt="GPL-3.0">
</p>

<p align="center">
  <a href="README.md">English</a> | <b>简体中文</b>
</p>

<p align="center"><i>PapersDelight 的附属插件 API：菜单框架、配方类型、物品匹配、伤害类型契约与能力门 —— 只提供契约，不含任何运行时实现。</i></p>

---

## 📖 这是什么

`PapersDelight-API` 是 **PapersDelight 插件与附属插件之间的契约**。它只包含接口、record 与门（Gate）——具体实现随 PapersDelight 插件 jar 一起分发，所以附属插件用 `compileOnly` 引入即可。

- 你按这个 artifact 写附属插件代码；
- 运行时由 PapersDelight 插件提供这些类（`join-classpath`）；
- 整个 `dev.tako.papersdelight.api` 包**豁免混淆**，保证跨版本链接稳定。

**我需要它吗？** 只有开发 PapersDelight 附属插件时才需要。服务器服主不需要单独安装它。

## 📦 里面有什么

| 包 | 作用 |
| --- | --- |
| `dev.tako.papersdelight.api` | `PapersDelightApi` —— API 版本契约（`VERSION`、`isCompatible(int)`） |
| `…api.menu` | GUI 契约：`Menu`、`MenuItem`、`MenuModule`、`SimpleMenuModule`、`MenuEventHandler`、`ClickHandler` 与注解辅助；以及 `MenuService`——插件通过 Bukkit 服务管理器发布的运行时句柄 |
| `…api.recipe` | `RecipeTypeRegistry` / `RecipeTypeHandler` —— 注册你自己的配方类型；它的输入是 CraftEngine 配置 section，实现它时请自行添加 CE 编译依赖 |
| `…api.damage` | `DamageTypeDefinition` / `DamageTypeHandle` / `DamageTypeRegistrationState` —— 自定义伤害类型的数据契约 |
| `…api.item` | 物品匹配器与高级标签门（`AdvancedTagGate`），用于把附属插件物品暴露给 PapersDelight 机制 |
| `…api.heat`、`…api.cold`、`…api.protection` | 门（Gate）：让附属插件告诉 PapersDelight 什么算热源/冷源，或某次交互是否受保护 |

这里的一切都是**契约**：接口、record、builder、门，以及少量零依赖小助手。本 artifact 刻意**不含菜单引擎、效果引擎、CraftEngine 工具与调度器**——运行时实现全部由 PapersDelight 主插件持有，因此引入这份 API 不会给你的附属插件带来任何第三方依赖。

唯一有意的平台耦合是 `RecipeTypeHandler`：它的参数类型本身就是 CraftEngine 配置 section（CE 是主插件的平台，不是可选附件）。反射调用也已经去掉，注解分发与 AntiGriefLib 保护桥接都走 `MethodHandle`，不再用 `Method#invoke`。

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
    compileOnly("dev.tako:papersdelight-api:4.0.0")
}
```

**Gradle（Groovy DSL）**

```groovy
dependencies {
    compileOnly 'dev.tako:papersdelight-api:4.0.0'
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
    <version>4.0.0</version>
    <scope>provided</scope>
</dependency>
```

> ℹ️ 这个 artifact **不声明任何依赖**，内容只有契约，请自带 Paper/Folia 编译目标（API 基于 `paper-api 1.21` 编译）；如果代码要用 CraftEngine 类型，自行添加 CE 依赖。
>
> ℹ️ 主插件持有的 GUI 引擎以服务形式暴露，而不是类：
>
> ```java
> MenuService menus = MenuService.get();   // 等价于 Bukkit.getServicesManager().load(MenuService.class)
> if (menus != null) menus.openMenu(player, "my_module");
> ```

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

[`example/`](example/) 里有一个完整可构建的示例：注册一个 GUI 模块、用附属插件自己的 Bukkit 调度跑持续效果，不触碰 API 的任何实现。

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

构建并试用：

```bash
cd example
../gradlew build       # 产物：example/build/libs/ExampleAddon-1.0.0.jar
```

## 🔢 版本与兼容性

- API 与插件本体**独立版本号**（这里是 `4.x`，插件是 `PapersDelight 1.2.x`）。
- **4.0.0 是不兼容版本**：主插件内部的 CraftEngine 配置 parser 基类（`GenerationAwareIdSectionConfigParser`、`ParserGeneration`）移出 API；反射调用换成 `MethodHandle`（`AnnotationHandlerRegistrar`、`ProtectionGate`）。
- **3.0.1** 只是把文档里已经承诺的单向 `DamageTypeHandle#downgrade()` 公开，其余没变。
- **3.0.0 是不兼容版本**：所有实现都移出 API。`MenuManager`、`TimedEffectManager`、`EffectPdcStore`、`CraftEngineUtil`、`DamageTypes` 与旧的 `api.util` 工具现在都在 PapersDelight 主插件里，API 也不再依赖 CC-Scheduler（主插件内部依然用 CC-Scheduler 调度，那是插件自己的事）。
- 需要主插件的 GUI 引擎时，它是以服务形式发布的：`MenuService.get()`，返回 `null` 说明没装 PapersDelight 或版本过旧。
- 次版本号只做**新增**；兼容区间由 `PapersDelightApi.MINIMUM_COMPATIBLE_VERSION` ~ `PapersDelightApi.VERSION` 表达。
- 启动时请用 `PapersDelightApi.isCompatible(你编译时的版本)` 判断，而不是硬编码版本号。
- `dev.tako.papersdelight.api.*` 豁免混淆：类名与方法名都属于对外契约。

## 🙏 致谢

- **作者：** Shimamura Tako、Mr Dg32z_、gukuan、Cold Leaves
- **技术支持：** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine)、[Paper](https://github.com/PaperMC/Paper)、[Folia](https://github.com/PaperMC/Folia)

## 📄 授权

本项目采用 **GNU General Public License v3.0**（GPL-3.0）——全文见 [LICENSE](LICENSE)。

PapersDelight 插件本体单独分发；本仓库只包含面向附属插件的 API。
