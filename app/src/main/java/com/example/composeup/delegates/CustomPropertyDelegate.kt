package com.example.composeup.delegates

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 自定义属性委托。
 *
 * 当标准库的委托不够用，而某段「读写逻辑」又要在多个属性间复用时，就自己写委托：
 *  - 可变属性 (var)：实现 ReadWriteProperty，提供 getValue / setValue
 *  - 只读属性 (val)：实现 ReadOnlyProperty，只需提供 getValue
 *
 * 委托方法的参数含义：
 *  - thisRef：拥有该属性的对象（谁 by 了它）
 *  - property：属性的元信息，property.name 就是属性名
 *  - value：setValue 时传入的新值
 */

// ---------------------------------------------------------------------------
// 场景 A：把属性「持久化」到键值存储（模拟 SharedPreferences）
// ---------------------------------------------------------------------------

/** 简化的键值存储，实际项目里可换成 Android 的 SharedPreferences / DataStore */
class PrefStore {
    private val data = mutableMapOf<String, Any?>()

    fun getInt(key: String, default: Int): Int = data[key] as? Int ?: default
    fun putInt(key: String, value: Int) {
        data[key] = value
    }
}

/** 把属性绑定到 PrefStore，用属性名当 key，实现「读写属性 = 读写存储」 */
class PrefInt(
    private val store: PrefStore,
    private val default: Int,
) : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
        store.getInt(property.name, default)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
        store.putInt(property.name, value)
}

class UserSettings(store: PrefStore) {
    // 看起来是普通字段，实际上值都自动存进了 store，非常适合「用户偏好设置」
    var fontSize: Int by PrefInt(store, default = 14)
    var launchCount: Int by PrefInt(store, default = 0)
}

// ---------------------------------------------------------------------------
// 场景 B：可复用的「范围约束」委托
// ---------------------------------------------------------------------------

/**
 * vetoable 的校验逻辑写在每个属性后面，无法复用。
 * 封装成委托后，任何需要「把值限制在某个区间」的属性都能一行接入。
 */
class ClampedInt(
    private val range: IntRange,
    initialValue: Int,
) : ReadWriteProperty<Any?, Int> {
    private var value: Int = initialValue.coerceIn(range)

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        // 超出范围自动收敛到最近的边界，而不是拒绝
        this.value = value.coerceIn(range)
    }
}

class AudioSettings {
    var volume: Int by ClampedInt(0..100, initialValue = 50)
}

// ---------------------------------------------------------------------------
// 场景 C：只读委托（ReadOnlyProperty）——「带日志的 lazy」
// ---------------------------------------------------------------------------

/** 演示只读属性委托：首次读取时计算并打印日志，之后返回缓存值 */
class LoggingLazy<T>(
    private val initializer: () -> T,
) : ReadOnlyProperty<Any?, T> {
    private var value: Any? = UNINITIALIZED

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (value === UNINITIALIZED) {
            value = initializer()
            println("  [LoggingLazy] 首次计算 ${property.name} = $value")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private companion object {
        val UNINITIALIZED = Any()
    }
}

class Report {
    // 报表数据较重，首次访问才计算，并打印一条日志
    val summary: String by LoggingLazy {
        "共 42 条记录"
    }
}

// ---------------------------------------------------------------------------
// 场景 D：provideDelegate —— 在「属性绑定委托」的那一刻介入
// ---------------------------------------------------------------------------

/**
 * getValue / setValue 是「每次读写」时触发；而 provideDelegate 是「属性被创建、
 * 把委托绑定上去」的那一刻只触发一次，还能拿到属性元信息（名字、注解、类型）。
 *
 * 所以它专门做「只需一次、且依赖属性元信息」的事，典型场景：
 *   - 绑定即校验：属性名/注解不合法，就在【构造对象时】立刻报错，尽早暴露问题；
 *   - 绑定即注册：把属性登记进注册表，方便统一遍历、批量持久化、埋点统计。
 *
 * 求值顺序（ val/var x by Delegate() ）：
 *   1) 先执行 Delegate().provideDelegate(this, ::x)，返回「真正」的委托对象；
 *   2) 之后对 x 的每一次读写，才走返回对象里的 getValue / setValue。
 */

/** 记录「哪些字段被绑定过」的注册表，可在运行时枚举全部字段 */
class ConfigRegistry {
    private val defaults = linkedMapOf<String, String>()

    fun register(key: String, default: String) {
        defaults[key] = default
    }

    fun keys(): Set<String> = defaults.keys

    fun defaults(): Map<String, String> = defaults.toMap()
}

/**
 * 委托「工厂」：绑定时先校验属性名、再登记到注册表，最后返回真正存值的委托。
 * 关键点：provideDelegate 必须是 operator fun，它的返回值才是真正的属性委托。
 */
class ConfigField(
    private val registry: ConfigRegistry,
    private val default: String,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadWriteProperty<Any?, String> {
        // ① 绑定即校验：要求字段名小写开头，否则构造对象时就抛异常（而不是等到读写才发现）
        require(prop.name.first().isLowerCase()) {
            "配置字段名必须小写开头，当前为 '${prop.name}'"
        }
        // ② 绑定即注册：把 key 和默认值登记进注册表
        registry.register(prop.name, default)
        // ③ 返回真正负责读写的委托
        return InMemoryString(default)
    }
}

/** 最朴素的字符串读写委托：把值存在自己的字段里 */
class InMemoryString(initial: String) : ReadWriteProperty<Any?, String> {
    private var value = initial
    override fun getValue(thisRef: Any?, property: KProperty<*>): String = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        this.value = value
    }
}

/** 正常用法：theme / language 两个字段在 AppSettings 构造时就被自动登记 */
class AppSettings(registry: ConfigRegistry) {
    var theme: String by ConfigField(registry, default = "light")
    var language: String by ConfigField(registry, default = "zh")
}

/** 错误用法：字段名大写开头，构造 BadSettings 时 provideDelegate 里的 require 就会抛异常 */
class BadSettings(registry: ConfigRegistry) {
    var Theme: String by ConfigField(registry, default = "light")
}
