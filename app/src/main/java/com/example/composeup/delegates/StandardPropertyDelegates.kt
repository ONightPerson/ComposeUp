package com.example.composeup.delegates

import kotlin.properties.Delegates

/**
 * Kotlin 标准库内置「属性委托」的实际场景示例。
 *
 * 语法：val/var 属性: 类型 by 委托对象
 * 含义：把这个属性的 getter（以及 var 的 setter）逻辑，交给 by 后面的委托对象来处理。
 *
 * 本文件演示标准库自带的几种委托，每种都配一个贴近实战的场景：
 *   1. lazy           —— 延迟初始化昂贵对象
 *   2. observable     —— 监听属性变化（做副作用）
 *   3. vetoable       —— 校验并否决非法赋值
 *   4. notNull        —— 非空的「两阶段初始化」
 *   5. by map         —— 用 Map 存储属性值（解析动态数据）
 */

// ---------------------------------------------------------------------------
// 1. lazy —— 延迟初始化
// ---------------------------------------------------------------------------

/**
 * 场景：配置管理类。
 *
 * settings 的加载要读磁盘、解析文件，成本较高，而且程序里不一定用得到。
 * 用 lazy 后，只有在「第一次真正读取 settings」时才会执行 loadFromDisk()，
 * 之后每次读取都直接返回同一个已缓存的结果，不会重复加载。
 */
class AppConfig private constructor() {

    val settings: Map<String, String> by lazy {
        println("  [lazy] 首次访问 settings，正在从磁盘加载配置...")
        loadFromDisk()
    }

    private fun loadFromDisk(): Map<String, String> =
        mapOf("theme" to "dark", "language" to "zh-CN")

    companion object {
        // 单例的标准写法：全局唯一，且第一次用到时才创建实例
        val instance: AppConfig by lazy { AppConfig() }
    }
}

/**
 * lazy 的三种线程安全模式（LazyThreadSafetyMode）：
 *  - SYNCHRONIZED（默认）：加锁，确保初始化只执行一次，最安全，适合多线程
 *  - PUBLICATION：允许多线程同时初始化，但最终只采用最先完成的那个值
 *  - NONE：完全不加锁，性能最好，只在「确定只有单线程访问」时使用
 */
object LazyModes {
    // 只在主线程访问，用 NONE 省掉加锁开销
    val uiCache: StringBuilder by lazy(LazyThreadSafetyMode.NONE) {
        StringBuilder("cached-data")
    }
}

// ---------------------------------------------------------------------------
// 2. observable —— 监听变化
// ---------------------------------------------------------------------------

/**
 * 场景：用户资料。
 *
 * 属性改变后需要「顺带做点事」：打日志、埋点、通知其它模块刷新等。
 * observable 的回调在【新值已经赋上之后】触发，能同时拿到旧值和新值。
 */
class UserProfile {

    var nickname: String by Delegates.observable("游客") { prop, old, new ->
        println("  [observable] ${prop.name}: \"$old\" -> \"$new\"")
    }

    var level: Int by Delegates.observable(1) { _, old, new ->
        println("  [observable] 会员等级 $old -> $new，去解锁对应权益")
    }
}

// ---------------------------------------------------------------------------
// 3. vetoable —— 否决非法赋值
// ---------------------------------------------------------------------------

/**
 * 场景：银行账户 / 空调温控。
 *
 * 与 observable 最大的区别：vetoable 的回调在【赋值发生之前】触发，
 * 返回 true 才允许这次修改；返回 false 则「否决」，属性保持原值。
 * 非常适合做数据校验、边界保护。
 */
class BankAccount {

    // 余额不允许为负：newValue >= 0 才接受
    var balance: Int by Delegates.vetoable(0) { _, _, newValue ->
        val accept = newValue >= 0
        if (!accept) println("  [vetoable] 拒绝把余额设为 $newValue（不能透支）")
        accept
    }
}

class Thermostat {

    // 温度只允许 16~30 度，超出范围的赋值一律被否决
    var temperature: Int by Delegates.vetoable(26) { _, _, newValue ->
        newValue in 16..30
    }
}

// ---------------------------------------------------------------------------
// 4. notNull —— 非空的延迟初始化
// ---------------------------------------------------------------------------

/**
 * 场景：订单处理器的两阶段初始化。
 *
 * 对象刚构造时还拿不到某些值，要等外部（如 onCreate、依赖注入、反序列化）稍后设置。
 * notNull 让你把属性声明成非空类型，读取前若没赋值会抛异常。
 *
 * 对比 lateinit：lateinit 只支持引用类型，不支持 Int/Long/Boolean 等基本类型；
 * 而 notNull 支持基本类型，正好补上这个缺口。
 */
class OrderProcessor {

    var retryTimes: Int by Delegates.notNull()
    var timeoutMillis: Long by Delegates.notNull()

    fun process() {
        println("  [notNull] retryTimes=$retryTimes, timeoutMillis=$timeoutMillis")
    }
}

// ---------------------------------------------------------------------------
// 5. by map —— 用 Map 存储属性值
// ---------------------------------------------------------------------------

/**
 * 场景：解析 JSON / 键值对等「结构在运行时才确定」的数据。
 *
 * 属性值不存进类的字段，而是以「属性名」为 key 存到传入的 Map 中。
 * 这样无需手写大量解析代码，属性名与 map 的 key 自动对应。
 */
class Person(map: Map<String, Any?>) {
    val name: String by map
    val age: Int by map
    val email: String? by map
}
