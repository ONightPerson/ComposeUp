# CompositionLocal 深度应用指南

`CompositionLocal` 是 Compose 提供的「属性钻取（Prop Drilling）」终结者。它允许数据在 UI 树中隐式传递，特别适合处理**横切关注点（Cross-cutting Concerns）**。

---

## 一、 核心概念对比

| 特性 | `compositionLocalOf` | `staticCompositionLocalOf` |
| :--- | :--- | :--- |
| **重组粒度** | **局部重组**。仅使用该值的组件会重组。 | **全局重组**。值变化时，Provider 下的所有内容都会重组。 |
| **性能开销** | 追踪依赖项，读取略慢，但重组更高效。 | 不追踪依赖，读取极快，但重组开销大。 |
| **适用场景** | 频繁变化的值（如当前动画进度、播放状态）。 | 极少变化的值（如当前翻译、主题颜色、Repository 实例）。 |

---

## 二、 本模块演示案例

### 1. 动态多语言切换 ([LocalizationDemo.kt](file:///Users/xlgd/workspace/ComposeUp/app/src/main/java/com/example/composeup/compositionlocal/LocalizationDemo.kt))
*   **痛点**：原生 `stringResource` 强绑定 Activity 资源配置，切换语言需重启。
*   **解法**：通过 `LocalAppStrings` 提供一个纯 Kotlin 的 String 集合模型，实现秒级即时翻译刷新。

### 2. 全局权限请求控制器 ([PermissionDemo.kt](file:///Users/xlgd/workspace/ComposeUp/app/src/main/java/com/example/composeup/compositionlocal/PermissionDemo.kt))
*   **痛点**：在深层嵌套的按钮中申请权限，需要逐层传递 `launcher`，代码冗余且难以维护。
*   **解法**：在根部提供 `LocalPermissionHandler` 接口，任何组件通过 `.current` 即可发起权限请求。

### 3. 自定义设计系统扩展 ([DesignSystemDemo.kt](file:///Users/xlgd/workspace/ComposeUp/app/src/main/java/com/example/composeup/compositionlocal/DesignSystemDemo.kt))
*   **痛点**：Material3 虽好，但往往无法满足特定设计的「间距（Spacing）」或「阴影」维度。
*   **解法**：扩展 `AppTheme.spacing` 访问器，实现类似 `MaterialTheme.colorScheme` 的类型安全访问。

### 4. 埋点统计装饰器 ([AnalyticsDemo.kt](file:///Users/xlgd/workspace/ComposeUp/app/src/main/java/com/example/composeup/compositionlocal/AnalyticsDemo.kt))
*   **应用**：展示「装饰器模式」。嵌套的 `AnalyticsSection` 能够读取上层 Tracker 并合并自己的上下文（如 `section_id`），从而实现自动化的埋点路径追踪。

---

## 三、 架构设计黄金定律

1.  **显式优于隐式**：如果数据只被 1-2 层组件使用，请直接传递参数。过度使用 `CompositionLocal` 会使组件变成「黑盒」，增加调试难度。
2.  **默认值保护**：始终为 `CompositionLocal` 准备一个合理的默认值或清晰的报错信息。
3.  **稳定引用**：`CompositionLocalProvider` 提供的值应该是稳定的（使用 `remember`），避免父组件重组导致不必要的 Local 值变化。
