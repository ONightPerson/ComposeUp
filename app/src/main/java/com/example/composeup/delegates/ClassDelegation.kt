package com.example.composeup.delegates

/**
 * 类委托（Class Delegation）—— by 关键字的「另一种」用法。
 *
 * 别和属性委托搞混：
 *  - 属性委托： val/var 属性 by 委托对象            委托「一个属性」的读写
 *  - 类委托：   class Foo(impl) : 接口 by impl      委托「整个接口」的实现
 *
 * 类委托解决什么问题？
 *  想在已有实现上「加一点行为」，又不想手写一堆转发方法——这正是「装饰器模式」。
 *  用 `by impl` 后，接口里你没重写的方法会自动转发给 impl，只写你要增强的那几个即可。
 */

interface UserRepository {
    fun getUser(id: Int): String
    fun saveUser(id: Int, name: String)
}

/** 真正干活的实现（比如访问数据库 / 网络） */
class RemoteUserRepository : UserRepository {
    private val db = mutableMapOf<Int, String>()

    override fun getUser(id: Int): String = db[id] ?: "unknown-$id"

    override fun saveUser(id: Int, name: String) {
        db[id] = name
    }
}

/**
 * 装饰器一：加「日志」能力。
 * 只重写 saveUser，getUser 由 `by impl` 自动委托，无需手写转发。
 */
class LoggingUserRepository(
    private val impl: UserRepository,
) : UserRepository by impl {
    override fun saveUser(id: Int, name: String) {
        println("  [类委托] 保存前记日志：id=$id, name=$name")
        impl.saveUser(id, name) // 需要时手动调用被委托对象
    }
}

/**
 * 装饰器二：加「缓存」能力。
 * 装饰器可以像套娃一样层层叠加，每一层只关心自己的职责。
 */
class CachingUserRepository(
    private val impl: UserRepository,
) : UserRepository by impl {
    private val cache = mutableMapOf<Int, String>()

    override fun getUser(id: Int): String = cache.getOrPut(id) {
        println("  [类委托] 缓存未命中，回源查询 id=$id")
        impl.getUser(id)
    }
}