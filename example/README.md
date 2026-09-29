# ExampleAddon

A minimal, buildable PapersDelight addon. Everything it does is **API contract usage plus plain Bukkit** — no API implementation, no third-party scheduler:

| File | Shows |
| --- | --- |
| `ExampleAddon.java` | plugin bootstrap, `PapersDelightApi.isCompatible`, fetching `MenuService` from the Bukkit service manager, registering a GUI module, Bukkit-scheduled heartbeat |
| `ExampleEffect.java` | a timed glow effect the addon owns itself: Bukkit scheduler for expiry, cleanup on quit/death |
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
3. Start the server, join with an operator account and run `/example`… the menu module id is `example`, so any code can open it with `MenuService.get().openMenu(player, "example")`.

## 中文说明

这是一个最小可构建的 PapersDelight 附属插件示例：**只使用 API 契约 + 原生 Bukkit**，不依赖 API 的任何实现，也不引入第三方调度器。

- **插件启动**：检查 API 兼容性、通过 Bukkit 服务管理器拿到 `MenuService`、注册 GUI 模块、用 Bukkit 调度器跑心跳任务；
- **持续效果**：效果由附属插件自己实现（`ExampleEffect`），用 Bukkit 调度器处理到期，并在退出/死亡时清理；
- **菜单**：用 `Menu`/`MenuItem` 搭布局，用 `MenuEventHandler` 处理点击。

构建：`cd example && ../gradlew build` → `example/build/libs/ExampleAddon-1.0.0.jar`。
运行：把 PapersDelight（以及 CraftEngine 与其资源包）和本示例 jar 放进 `plugins/`，用 OP 账号执行 `/example`，模块 id 为 `example`。
