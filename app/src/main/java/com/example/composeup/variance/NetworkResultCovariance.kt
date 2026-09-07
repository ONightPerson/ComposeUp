package com.example.composeup.variance

import com.example.composeup.sealedtypes.NetworkResult

/**
 * 为什么 NetworkResult 的泛型要写成 <out T>？—— 用实例讲清「协变 (covariance)」。
 *
 * 一句话结论：
 *   out = 协变，它让「NetworkResult<子类型>」自动成为「NetworkResult<父类型>」的子类型。
 *   正因为有 out，声明成 NetworkResult<Nothing> 的 Failure / Loading，
 *   才能被当作任意 NetworkResult<T> 来用（因为 Nothing 是所有类型的子类型）。
 *
 * 两个前置知识：
 *   1) Nothing 是 Kotlin 里的「底部类型」，它是所有类型的子类型；
 *   2) 协变规则：若 A 是 B 的子类型，则 NetworkResult<A> 也是 NetworkResult<B> 的子类型。
 *      —— 这个「子类型关系的传递」正是 out 提供的。
 *      若没有 out，泛型默认「不变 (invariant)」：NetworkResult<A> 和 NetworkResult<B>
 *      之间没有任何父子关系，哪怕 A 是 B 的子类型。
 *
 * 为什么这里能安全地用 out（out 的限制）：
 *   被 out 标注的 T 只能出现在「输出位置」（如只读属性 val data、函数返回值），
 *   不能出现在「输入位置」（如函数参数）。NetworkResult 内部没有任何消费 T 的成员，
 *   T 仅作为 Success 的只读数据类型出现，因此满足协变要求。
 *   （反过来，若某个类型参数只被「消费」，则用 in 标注为逆变，此处不涉及。）
 */

// 一个普通的继承体系，用来演示协变的「子类型关系传递」
open class Animal(val name: String) {
    override fun toString(): String = name
}

class Dog(name: String) : Animal(name)

class Cat(name: String) : Animal(name)

fun main() {
    println("========== 实例1：Loading / Failure / Success 能放进同一个 List ==========")
    // Loading、Failure 是 NetworkResult<Nothing>，Success 是 NetworkResult<String>。
    // 只有 out 协变，才能把三者统一成 List<NetworkResult<String>>。
    val results: List<NetworkResult<String>> = listOf(
        NetworkResult.Loading,                  // NetworkResult<Nothing> 协变为 NetworkResult<String>
        NetworkResult.Success("用户数据"),        // 本来就是 NetworkResult<String>
        NetworkResult.Failure(
            500,
            "服务器错误"
        ), // NetworkResult<Nothing> 协变为 NetworkResult<String>
    )
    results.forEach { println("  $it") }

    println("\n========== 实例2：NetworkResult<Dog> 可赋值给 NetworkResult<Animal> ==========")
    val dogResult: NetworkResult<Dog> =
        NetworkResult.Success(Dog("旺财"))
    // Dog 是 Animal 的子类型，加上 NetworkResult 是 out 协变的，所以下面这行成立：
    val animalResult: NetworkResult<Animal> =
        dogResult   // ← 没有 out 这行会编译报错
    println("  dogResult 直接当作 animalResult 使用：$animalResult")

    println("\n========== 实例3：声明返回 NetworkResult<String>，却能直接 return Loading ==========")
    // Loading 是 NetworkResult<Nothing>，靠协变才能作为 NetworkResult<String> 返回
    println("  fetchUser(loading=true)  = ${fetchUser(loading = true)}")
    println("  fetchUser(loading=false) = ${fetchUser(loading = false)}")
}

/** 返回类型写死为 NetworkResult<String>，两个分支分别返回 Loading 和 Success，都能通过 */
fun fetchUser(loading: Boolean): NetworkResult<String> =
    if (loading) NetworkResult.Loading else NetworkResult.Success(
        "Alice"
    )

/*
 * ============ 反例：如果把 out 去掉（改成不变的 NetworkResult<T>）会怎样 ============
 * 类的声明本身仍能编译（Loading : NetworkResult<Nothing>() 只是指定了类型实参而已），
 * 但一到「使用」就处处报错，因为不变的 NetworkResult<Nothing> 与 NetworkResult<String>
 * 之间没有任何父子关系：
 *
 *   val results: List<NetworkResult<String>> =
 *       listOf(NetworkResult.Loading, NetworkResult.Success("x"))
 *   // [报错] Loading 是 NetworkResult<Nothing>，不能放进 List<NetworkResult<String>>
 *
 *   fun fetchUser(): NetworkResult<String> = NetworkResult.Loading
 *   // [报错] 类型不匹配：NetworkResult<Nothing> 不是 NetworkResult<String>
 *
 *   val animalResult: NetworkResult<Animal> = dogResult   // dogResult: NetworkResult<Dog>
 *   // [报错] NetworkResult<Dog> 不是 NetworkResult<Animal> 的子类型
 *
 * 有人会想：那干脆让 Failure/Loading 继承 NetworkResult<Any?> 不就行了？
 *   —— 能编译，但语义错了：那样 Loading 只能是 NetworkResult<Any?>，无法适配任意的 T；
 *   而 Nothing + out 的组合，既类型安全又能自由适配任意 NetworkResult<T>，才是标准写法
 *   （Kotlin 标准库的 kotlin.Result<out T> 正是这么做的）。
 */
