package com.example.composeup.sealedtypes

/**
 * 运行本文件的 main，观察密封类/接口配合 when 的「穷尽性」与「智能转换」。
 *
 * 重点体会：
 *  - when 覆盖了密封类型的全部子类后，不用写 else；
 *  - 在 is 分支里会自动智能转换，直接访问该子类独有的字段；
 *  - 若给某个密封类型新增一个子类，下面所有相关的 when 都会编译报错，提醒你补全。
 */
fun main() {
    println("========== 1. 网络请求结果 NetworkResult ==========")
    val results: List<NetworkResult<String>> = listOf(
        NetworkResult.Loading,
        NetworkResult.Success("用户列表"),
        NetworkResult.Failure(404, "Not Found"),
    )
    results.forEach { result ->
        val msg = when (result) {
            is NetworkResult.Success -> "成功: ${result.data}"                 // 智能转换，直接取 .data
            is NetworkResult.Failure -> "失败(${result.code}): ${result.message}" // 直接取 .code/.message
            NetworkResult.Loading -> "加载中..."                               // data object 用等值匹配
        }
        println("  $msg")
    }

    println("\n========== 2. UI 状态 UiState ==========")
    val states: List<UiState> = listOf(
        UiState.Idle,
        UiState.Loading(60),
        UiState.Content(listOf("A", "B")),
        UiState.Error("网络异常"),
    )
    states.forEach { state ->
        when (state) {
            UiState.Idle -> println("  渲染：空页面")
            is UiState.Loading -> println("  渲染：进度条 ${state.progress}%")
            is UiState.Content -> println("  渲染：列表 ${state.items}")
            is UiState.Error -> println("  渲染：错误提示 ${state.message}")
        }
    }

    println("\n========== 3. 播放器状态机 PlayerState ==========")
    val playerStates: List<PlayerState> = listOf(
        PlayerState.Idle,
        PlayerState.Buffering(80),
        PlayerState.Playing(12_000),
        PlayerState.Error("解码失败"),
    )
    playerStates.forEach { s ->
        val tip = when (s) {
            PlayerState.Idle -> "待机，可播放"
            is PlayerState.Buffering -> "缓冲中 ${s.percent}%"
            is PlayerState.Playing -> "播放中 @${s.positionMs}ms"
            is PlayerState.Error -> "出错：${s.cause}"
        }
        println("  canPlay=${s.canPlay}  -> $tip")   // canPlay 来自密封父类的抽象成员
    }

    println("\n========== 4. sealed interface 子类还能继承基类 ==========")
    val buttons: List<Button> = listOf(
        SubmitButton("提交"),
        CancelButton("取消"),
        IconButton("收藏", "★"),
    )
    buttons.forEach { button ->
        val action = when (button) {          // 穷尽 when，无需 else
            is SubmitButton -> "提交表单"
            is CancelButton -> "取消返回"
            is IconButton -> "点击图标 ${button.icon}"
        }
        println("  动作：$action")
    }
    // SubmitButton 既属于 Button 受限层级，又继承了 Component 的能力，可直接调用 log
    SubmitButton("提交").log("既能被 when 穷尽，又能调用基类方法")

    println("\n========== 5. 递归表达式树求值 (1 + 2) * 3 ==========")
    val expr = Expr.Mul(
        left = Expr.Add(Expr.Num(1), Expr.Num(2)),
        right = Expr.Num(3),
    )
    println("  求值结果 = ${evaluate(expr)}")
}

/** 递归求值：when 穷尽所有 Expr 子类，无需 else，也不可能漏掉某种表达式 */
fun evaluate(expr: Expr): Int = when (expr) {
    is Expr.Num -> expr.value
    is Expr.Add -> evaluate(expr.left) + evaluate(expr.right)
    is Expr.Mul -> evaluate(expr.left) * evaluate(expr.right)
    is Expr.Neg -> -evaluate(expr.expr)
}
