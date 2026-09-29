# ExampleAddon

A minimal, buildable PapersDelight addon that demonstrates the three most common API entry points:

| File | Shows |
| --- | --- |
| `ExampleAddon.java` | plugin bootstrap, API compatibility check, GUI module registration, `PaperScheduler` usage |
| `ExampleEffect.java` | `TimedEffectManager` — a custom timed effect with boss bar timer and automatic PDC persistence |
| `ExampleMenu.java` / `ExampleMenuHandler.java` | `Menu` + `MenuItem` layout and click handling through `MenuEventHandler` |

## Build

```bash
cd example
../gradlew build
```

Result: `example/build/libs/ExampleAddon-1.0.0.jar`

## Run

1. Put `PapersDelight-<version>.jar` (and CraftEngine + its resource pack) into your test server's `plugins/`.
2. Put the built `ExampleAddon-1.0.0.jar` into `plugins/`.
3. Start the server, join and run `/example`… the menu module id is `example`, so any addon code can open it with `MenuManager.getInstance().openMenu(player, "example")`.

## 中文说明

这是一个最小可构建的 PapersDelight 附属插件示例，演示三个最常用的 API 入口：

- **插件启动**：检查 API 兼容性、注册 GUI 模块、使用 `PaperScheduler`（Folia 安全）；
- **持续效果**：继承 `TimedEffectManager`，自动获得 BossBar 计时与 PDC 持久化；
- **菜单**：用 `Menu`/`MenuItem` 搭布局，用 `MenuEventHandler` 处理点击。

构建：`cd example && ../gradlew build` → `example/build/libs/ExampleAddon-1.0.0.jar`。
运行：把 PapersDelight（以及 CraftEngine 与其资源包）和本示例 jar 放进 `plugins/`，启动服务器后打开模块 id 为 `example` 的菜单即可。
