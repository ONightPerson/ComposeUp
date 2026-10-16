---
kind: external_dependency
name: Jetpack Navigation 3（nav3）——返回栈归应用的导航框架
slug: jetpack-navigation-3
category: external_dependency
category_hints:
    - framework_behavior
    - sdk_real_api
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/com/example/composeup/navigation3/README.md
---

### 身份与用途
AndroidX 官方导航库的下一代实现，替代 Navigation 2 的 `NavController` 模型。本项目在 `app/src/main/java/com/example/composeup/navigation3/` 下用 8 个示例覆盖其全部能力：返回栈、ViewModel 作用域、返回结果、metadata、模块化、Deep Links、Scenes、NavEntryDecorator。

### 依赖版本与集成点
- `org.jetbrains.kotlinx:kotlinx-serialization`（用于 NavKey 序列化与 Deep Link URI 参数抽取）

### 稳定使用模式（代码不可见的心智模型）
- Scene 计算走策略：`SceneStrategy.calculateScene` / `SceneDecoratorStrategy.decorateScene` 均为 `Scope` 扩展函数，按顺序匹配，null 则回退默认 `SinglePaneSceneStrategy`。
