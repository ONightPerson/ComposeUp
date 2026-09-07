package com.example.composeup.variance

/**
 * Kotlin 型变 (variance) 的三个方向一次理清：out(协变) / in(逆变) / 不变(invariant)。
 *
 * 记忆口诀（以 Dog <: Animal 为例）：
 *   - out T（生产者/只读，T 只在「输出位置」）：子类型关系「同向」  Box<Dog> <: Box<Animal>
 *   - in  T（消费者/只写，T 只在「输入位置」）：子类型关系「反向」  Trainer<Animal> <: Trainer<Dog>
 *   - 不加标注（不变，既可读又可写）：           两个方向都没有父子关系
 *
 * 直觉：
 *   - 协变(out)：一个「装 Dog 的盒子」当然也是「装 Animal 的盒子」——只往外拿，安全。
 *   - 逆变(in) ：一个「能训练 Animal 的驯兽师」当然也「能训练 Dog」——只往里喂，安全。
 *   - 不变    ：一个「可变容器」既能拿又能放，向上/向下转型都可能破坏类型安全，故禁止。
 *
 * 说明：out 的完整实例见 NetworkResultCovariance.kt；本文件用同一套 Animal / Dog
 * （定义在 NetworkResultCovariance.kt）来对照演示 in 与「不变」。
 */

/** 逆变接口：T 只出现在「输入位置」（函数参数），所以用 in 标注 */
interface Trainer<in T> {
    fun train(animal: T)
}

/** 一个能训练「任意 Animal」的驯兽师，自然也能训练 Animal 的任何子类（如 Dog） */
class AnimalTrainer : Trainer<Animal> {
    override fun train(animal: Animal) {
        println("  [AnimalTrainer] 训练：${animal.name}")
    }
}

fun main() {
    println("========== in 逆变：能处理父类型的，就能当处理子类型的用 ==========")
    // Dog <: Animal，但 in 让方向反过来：Trainer<Animal> <: Trainer<Dog>
    val dogTrainer: Trainer<Dog> = AnimalTrainer()   // ← 没有 in，这行会编译报错
    dogTrainer.train(Dog("旺财"))

    // 标准库的 Comparator<in T> 是逆变的经典例子：
    // sortedWith 需要 Comparator<in Dog>，传一个 Comparator<Animal> 正好可用
    val byName = Comparator<Animal> { a, b -> a.name.compareTo(b.name) }
    val dogs = listOf(Dog("旺财"), Dog("阿黄"), Cat("凯蒂"))
    val sorted = dogs.sortedWith(byName)
    println("  用 Comparator<Animal> 给 List<Dog> 排序：${sorted.map { it.name }}")

    println("\n========== 不变 (invariant)：MutableList<Dog> 不是 MutableList<Animal> ==========")
    val mutableDogs: MutableList<Dog> = mutableListOf(Dog("旺财"))
    // 下面这行若取消注释会编译报错，因为 MutableList<T> 是「不变」的：
//       val mutableAnimals: MutableList<Animal> = mutableDogs
    // 为什么必须不变？假设允许这样赋值：
    //   mutableAnimals.add(Cat())   // Cat 也是 Animal（另一种 Animal），编译器会放行
    //   可 mutableDogs 里就被塞进了一只 Cat，之后从 mutableDogs 取 Dog 时类型就崩了。
    //   为杜绝这种隐患，Kotlin 直接禁止 MutableList 的向上/向下转型。
    println("  MutableList<Dog> 只能当 MutableList<Dog> 用，无法转成 MutableList<Animal>")

    // 对比：只读 List<out T> 是协变的，所以下面成立（只能拿、不能放，故安全）
    val readOnlyAnimals: List<Animal> = mutableDogs   // List<Dog> <: List<Animal>
    println("  只读 List 协变：List<Dog> 可赋值给 List<Animal> -> ${readOnlyAnimals.map { it.name }}")

    println("\n========== 三方向速查（前提 Dog <: Animal）==========")
    println("  out(协变)  NetworkResult<Dog>  -> 可当 NetworkResult<Animal>  （同向，只读/生产）")
    println("  in (逆变)  Trainer<Animal>     -> 可当 Trainer<Dog>           （反向，只写/消费）")
    println("  不变       MutableList<Dog>    -> 不能当 MutableList<Animal>  （可读可写，禁止）")
}
