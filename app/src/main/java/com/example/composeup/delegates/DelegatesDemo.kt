package com.example.composeup.delegates

/**
 * 汇总演示入口。
 *
 * 直接运行本文件的 main 方法（在 IDE 里点击 main 左侧的运行按钮），
 * 就能在控制台看到每种委托的实际效果。
 */
fun main() {
    println("========== 1. lazy 延迟初始化 ==========")
    val config = AppConfig.instance
    println("  单例已创建，但此时 settings 还没加载")
    println("  第一次读取: ${config.settings}")
    println("  第二次读取: ${config.settings}   <- 直接复用，不再加载")

    println("\n========== 2. observable 监听变化 ==========")
    val user = UserProfile()
    user.nickname = "张三"
    user.level = 5

    println("\n========== 3. vetoable 否决非法赋值 ==========")
    val account = BankAccount()
    account.balance = 100
    println("  正常存入后余额 = ${account.balance}")
    account.balance = -50 // 会被否决
    println("  尝试设为 -50 后余额 = ${account.balance}   <- 保持原值")
    val thermostat = Thermostat()
    thermostat.temperature = 40 // 超出 16..30，被否决
    println("  空调设为 40 度后 = ${thermostat.temperature}   <- 被拒绝，保持 26")

    println("\n========== 4. notNull 两阶段初始化 ==========")
    val processor = OrderProcessor()
    processor.retryTimes = 3
    processor.timeoutMillis = 5000
    processor.process()

    println("\n========== 5. by map 解析动态数据 ==========")
    val person = Person(mapOf<String, Any?>("name" to "李四", "age" to 28, "email" to null))
    println("  name=${person.name}, age=${person.age}, email=${person.email}")

    println("\n========== 6. 自定义委托 ==========")
    val settings = UserSettings(PrefStore())
    settings.fontSize = 18
    println("  fontSize 写入后 = ${settings.fontSize}   <- 值其实存进了 PrefStore")
    val audio = AudioSettings()
    audio.volume = 999 // 超出范围，自动收敛到 100
    println("  volume 设为 999 后 = ${audio.volume}   <- 自动限制在 0..100")
    val report = Report()
    println("  第一次读 summary: ${report.summary}")
    println("  第二次读 summary: ${report.summary}   <- 不再重新计算")

    println("\n========== 7. provideDelegate 绑定即校验 / 注册 ==========")
    val registry = ConfigRegistry()
    val appSettings = AppSettings(registry) // 构造时 theme/language 已被自动登记
    appSettings.theme = "dark"
    println("  theme = ${appSettings.theme}")
    println("  构造后自动登记的字段 = ${registry.keys()}")
    println("  各字段默认值 = ${registry.defaults()}")
    // 绑定即校验：字段名不合法，在【构造对象时】就被拦下，而不是等到读写
    try {
        BadSettings(registry)
    } catch (e: IllegalArgumentException) {
        println("  构造阶段拦截非法字段 -> ${e.message}")
    }

    println("\n========== 8. 类委托（装饰器叠加） ==========")
    val repo = LoggingUserRepository(
        CachingUserRepository(
            RemoteUserRepository()
        )
    )
    repo.saveUser(1, "王五")
    println("  查询结果 = ${repo.getUser(1)}")
    println("  再次查询 = ${repo.getUser(1)}   <- 命中缓存，不再回源")
}
