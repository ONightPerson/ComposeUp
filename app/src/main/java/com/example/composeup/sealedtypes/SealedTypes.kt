package com.example.composeup.sealedtypes

/**
 * Sealed classes and interfaces（密封类与密封接口）—— 概念与实战场景。
 *
 * 【是什么】
 * 密封类/接口限制了继承层级：所有「直接子类」必须在编译期已知，
 * 且与父类位于同一个包、同一个模块（可以在不同文件里）。
 * 密封类本身是抽象的，不能被直接实例化。
 *
 * 【主要作用】
 * 1. 表达「一个值只可能是有限的、已知的几种类型之一」——天然适合状态、结果、事件的建模；
 * 2. 配合 when 实现「穷尽性检查」：
 *    - when 覆盖所有子类后，不需要写 else；
 *    - 一旦新增了子类，所有没处理它的 when 会【编译报错】，逼你补全每个分支，
 *      从根源上杜绝「漏掉某种情况」的 bug；
 * 3. 每个子类都能携带自己独有的数据/行为，比 enum 更灵活
 *    （enum 每个常量结构必须一样，密封类的每个子类可以完全不同）。
 *
 * 【sealed class vs sealed interface】
 * - sealed class：子类必须「继承」它，会占用唯一的继承名额；可带构造参数、共享状态。
 * - sealed interface（Kotlin 1.5+）：子类「实现」它，继承名额还留着，可再继承别的基类；
 *   更适合需要多继承能力的场景。
 */

// ---------------------------------------------------------------------------
// 场景 1：网络请求结果（sealed class）—— 最经典的用法
// ---------------------------------------------------------------------------

/**
 * 一次请求的结果，只可能是：成功 / 失败 / 加载中 三者之一。
 * 用密封类建模后，处理结果的 when 无需 else，也不可能漏掉某种情况。
 *
 * 说明：Success 携带数据、Failure 携带错误码与信息、Loading 无状态；
 * 三个子类结构各不相同，这正是 enum 难以做到的。
 */
sealed class NetworkResult<out T> {
    /** 成功：携带返回的数据 */
    data class Success<T>(val data: T) : NetworkResult<T>()

    /** 失败：携带错误码和错误信息 */
    data class Failure(val code: Int, val message: String) : NetworkResult<Nothing>()

    /** 加载中：无状态，用 data object 表示单例 */
    data object Loading : NetworkResult<Nothing>()
}

// ---------------------------------------------------------------------------
// 场景 2：界面状态（sealed interface）
// ---------------------------------------------------------------------------

/**
 * 一个页面的 UI 状态。Compose / ViewModel 里非常常见：
 * 根据当前状态渲染不同界面，新增状态时编译器会提醒你补全所有 when。
 */
sealed interface UiState {
    data object Idle : UiState
    data class Loading(val progress: Int) : UiState
    data class Content(val items: List<String>) : UiState
    data class Error(val message: String) : UiState
}

// ---------------------------------------------------------------------------
// 场景 3：播放器状态机（sealed class + 抽象成员）
// ---------------------------------------------------------------------------

/**
 * 密封类的子类可以重写父类的抽象成员、并携带各自独有的状态数据。
 * 这里每个状态都给出 canPlay，同时带上该状态特有的信息（进度、缓冲百分比、错误原因）。
 */
sealed class PlayerState {
    /** 当前状态下是否允许点击「播放」 */
    abstract val canPlay: Boolean

    data object Idle : PlayerState() {
        override val canPlay = true
    }

    data class Playing(val positionMs: Long) : PlayerState() {
        override val canPlay = false
    }

    data class Buffering(val percent: Int) : PlayerState() {
        override val canPlay = false
    }

    data class Error(val cause: String) : PlayerState() {
        override val canPlay = false
    }
}

// ---------------------------------------------------------------------------
// 场景 4：sealed interface 的独特优势 —— 子类还能继承别的基类
// ---------------------------------------------------------------------------

/** 一个通用基类：假设所有界面组件都需要它的日志能力 */
open class Component(val tag: String) {
    fun log(msg: String) = println("  [$tag] $msg")
}

/**
 * 受限层级：按钮只有这几种。这里用 sealed interface 而非 sealed class，
 * 是为了让子类在实现它的同时，还能继承 Component（继承名额不被占用）——
 * 这是 sealed class 做不到的。
 */
sealed interface Button {
    val text: String
}

class SubmitButton(override val text: String) : Component("submit"), Button
class CancelButton(override val text: String) : Component("cancel"), Button
class IconButton(override val text: String, val icon: String) : Component("icon"), Button

// ---------------------------------------------------------------------------
// 场景 5：递归密封类型 —— 表达式树（编译器 / 计算器常用）
// ---------------------------------------------------------------------------

/**
 * 用密封接口建模一棵表达式树：每个节点又持有 Expr，形成递归结构。
 * 求值时用 when 递归展开，既穷尽又类型安全——不可能出现「未知的表达式类型」。
 */
sealed interface Expr {
    data class Num(val value: Int) : Expr
    data class Add(val left: Expr, val right: Expr) : Expr
    data class Mul(val left: Expr, val right: Expr) : Expr
    data class Neg(val expr: Expr) : Expr
}
