package com.example.composeup.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferences DataStore 实战示例（数据层）。
 *
 * DataStore 是 Jetpack 推荐的键值/对象持久化方案，用来替代 SharedPreferences。
 * 相比 SharedPreferences 的优势：
 *   - 完全异步：基于协程与 Flow，不会阻塞主线程（SP 的首次加载/commit 可能卡主线程，甚至 ANR）；
 *   - 类型安全：用 Preferences.Key<T> 声明键，读写都是强类型，不会取错类型；
 *   - 事务化写入：edit { } 是一笔事务，读改写原子完成，中途抛异常自动回滚；
 *   - 数据以 Flow 暴露：值一变化就发出新数据，UI（Compose）能自动刷新。
 *
 * 两种 DataStore：
 *   - Preferences DataStore（本文件）：存键值对，无需预定义结构，最常用；
 *   - Proto DataStore：用 Protocol Buffers 存强类型对象，适合结构化数据（需 .proto 文件 + 插件，较重，见文末说明）。
 *
 * 【运行方式】DataStore 依赖 Android Context 与协程，不能像纯 Kotlin 示例那样用 JVM main 跑；
 * 需在真机/模拟器上运行（配合 SettingsScreen.kt，或写 instrumented test）。
 */

// ---------------------------------------------------------------------------
// 创建单例 DataStore：注意这里用的正是「属性委托」(by)
// ---------------------------------------------------------------------------

/**
 * by preferencesDataStore(name = "settings") 是一个属性委托——它把「DataStore 的创建与单例持有」
 * 逻辑委托出去，保证每个 Context 对同一个文件名只会有一个 DataStore 实例。
 *
 * 两条铁律：
 *   1) 必须声明在【顶层】，不能放进类里——否则每个持有者都会各建一个实例，
 *      同时对同一文件多个 DataStore 读写会破坏数据一致性；
 *   2) name 就是磁盘文件名（这里是 settings.preferences_pb），改名等于换了一份存储。
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// ---------------------------------------------------------------------------
// 用 Repository 封装读写：向 UI 暴露 Flow（读）与 suspend 函数（写）
// ---------------------------------------------------------------------------

class SettingsRepository(private val context: Context) {

    // 类型安全的键：不同数据类型用不同的 xxxPreferencesKey，键名是字符串、值有类型
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val FONT_SIZE = intPreferencesKey("font_size")
        val USERNAME = stringPreferencesKey("username")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
    }

    // ===== 场景 1：读取用户设置（响应式，返回 Flow）=====
    // data 是 Flow<Preferences>：磁盘值一旦变化就发出新值；用 ?: 提供「没存过时」的默认值。
    val isDarkMode: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.DARK_MODE] ?: false }

    val fontSize: Flow<Int> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.FONT_SIZE] ?: 14 }

    val username: Flow<String> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.USERNAME] ?: "游客" }

    // ===== 场景 2：写入设置（事务化 edit，suspend）=====
    // edit { } 内部拿到的是当前 Preferences 的可变副本，改完原子写回；抛异常则整体回滚。
    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.DARK_MODE] = enabled }
    }

    suspend fun setFontSize(size: Int) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.FONT_SIZE] = size }
    }

    suspend fun setUsername(name: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.USERNAME] = name }
    }

    // ===== 场景 3：首次启动 / 引导页标记 =====
    // 经典需求：判断用户是否看过引导页，从而决定进 App 先展示引导还是主界面。
    val hasCompletedOnboarding: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.COMPLETED_ONBOARDING] ?: false }

    suspend fun completeOnboarding() {
        context.settingsDataStore.edit { prefs -> prefs[Keys.COMPLETED_ONBOARDING] = true }
    }

    // ===== 场景 4：基于当前值做「原子」自增（计数器）=====
    // edit 里能读到最新值再改写，天然避免并发竞态——这是 SP 的 getX + putX 两步写法难以保证的。
    suspend fun increaseLaunchCount() {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.LAUNCH_COUNT] ?: 0
            prefs[Keys.LAUNCH_COUNT] = current + 1
        }
    }

    val launchCount: Flow<Int> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.LAUNCH_COUNT] ?: 0 }

    // ===== 场景 5：聚合多个偏好成一个 UI 状态对象 =====
    // 一次 data 流映射出整个界面的状态，Compose 直接消费，避免为每个字段单独 collect。
    val settingsUiState: Flow<SettingsUiState> = context.settingsDataStore.data
        .map { prefs ->
            SettingsUiState(
                isDarkMode = prefs[Keys.DARK_MODE] ?: false,
                fontSize = prefs[Keys.FONT_SIZE] ?: 14,
                username = prefs[Keys.USERNAME] ?: "游客",
                launchCount = prefs[Keys.LAUNCH_COUNT] ?: 0,
            )
        }

    // ===== 场景 6：清空全部数据（如「退出登录 / 恢复默认」）=====
    suspend fun clearAll() {
        context.settingsDataStore.edit { prefs -> prefs.clear() }
    }
}

/** 把多个偏好聚合成一个不可变的 UI 状态，方便 Compose 直接消费 */
data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val fontSize: Int = 14,
    val username: String = "游客",
    val launchCount: Int = 0,
)

/*
 * ============================ 补充说明 ============================
 *
 * 【何时改用 Proto DataStore】
 *   当数据是「结构化对象」且字段较多、需要强 schema 时（如用户档案、复杂配置），
 *   用 Proto DataStore 更合适：写 .proto 定义消息、加 protobuf 插件、实现 Serializer<T>，
 *   存取的都是类型安全的对象而非零散的键值对。代价是接入更重。简单键值对仍首选 Preferences。
 *
 * 【注意事项】
 *   1) 敏感数据（token、密码）不要明文存 DataStore；应使用 Keystore 或加密方案
 *      （新版可用 androidx.datastore:datastore-tink 对 DataStore 加密）。
 *   2) DataStore 适合「小而频繁」的键值数据，不要拿它存大文件/大列表（那该用数据库或文件）。
 *   3) 委托必须顶层声明（见上），且生产环境建议向 Repository 传入 applicationContext，避免泄漏 Activity。
 *   4) 读是 Flow（冷流），只有在被 collect 时才真正读盘；写是 suspend，必须在协程里调用。
 */
