package com.example.composeup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.composeup.animation.AnimationHub
import com.example.composeup.datastore.SettingsScreen
import com.example.composeup.flow.FlowHub
import com.example.composeup.keywords.KeywordsHub
import com.example.composeup.nestedscroll.NestedScrollHub
import com.example.composeup.screenrecord.ScreenRecordHub
import com.example.composeup.sideeffects.SideEffectsHub
import com.example.composeup.ui.theme.ComposeUpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeUpTheme {
                ComposeUpRoot()
            }
        }
    }
}

/** 主页面列表里的各个演示 Topic。 */
private enum class Destination(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Home("原示例 App", "最初的 Compose 基础示例：问候卡片列表 + 展开动画", Filled.Home),
    NestedScroll("嵌套滑动进阶", "折叠头部、自定义 dispatcher、同向 / 正交嵌套、Material3 协作", Filled.SwapVert),
    Animation("动画进阶", "值动画、Transition、进出场、列表增删、AnimationSpec、手势 Animatable", Filled.Animation),
    Keywords("Compose 底层关键字", "operator / infix / invoke / inline / crossinline / noinline", Filled.Code),
    Flow("Flow 全家桶", "Flow / StateFlow / SharedFlow、操作符、背压、生命周期收集，真实案例串联", Filled.Timeline),
    SideEffects("Compose 副作用", "LaunchedEffect / DisposableEffect / SideEffect / produceState / derivedStateOf / snapshotFlow 等", Filled.Sync),
    Settings("DataStore 设置页", "用 Preferences DataStore 持久化开关与文本设置", Filled.Settings),
    ScreenRecord("录屏演示", "Android 12+ 录屏功能，支持媒体库同步与管理", Filled.Videocam),
}

/**
 * 根界面：主页面是一个 Topic 列表，点击某一项跳转到对应演示；
 * 在演示页按系统返回键回到列表。
 */
@Composable
private fun ComposeUpRoot() {
    var destination by rememberSaveable { mutableStateOf<Destination?>(null) }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        val current = destination
        if (current == null) {
            DestinationList(
                modifier = contentModifier,
                onSelect = { destination = it },
            )
        } else {
            // 系统返回键先回到主列表，而不是直接退出 App
            BackHandler { destination = null }
            when (current) {
                Destination.Home -> ComposeUpApp(contentModifier)
                Destination.NestedScroll -> NestedScrollHub(contentModifier)
                Destination.Animation -> AnimationHub(contentModifier)
                Destination.Keywords -> KeywordsHub(contentModifier)
                Destination.Flow -> FlowHub(contentModifier)
                Destination.SideEffects -> SideEffectsHub(contentModifier)
                Destination.Settings -> SettingsScreen(contentModifier)
                Destination.ScreenRecord -> ScreenRecordHub(contentModifier)
            }
        }
    }
}

/** 主页面：可点击跳转的 Topic 列表。 */
@Composable
private fun DestinationList(
    modifier: Modifier = Modifier,
    onSelect: (Destination) -> Unit,
) {
    val destinations = remember { Destination.entries.toList() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "ComposeUp",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Compose 学习示例合集 · 点击任意一项进入对应 Topic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(destinations) { destination ->
            DestinationRow(
                destination = destination,
                onClick = { onSelect(destination) },
            )
        }
    }
}

/** 列表里的一行：图标 + 标题 + 副标题 + 右侧箭头，整行可点击。 */
@Composable
private fun DestinationRow(
    destination: Destination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = destination.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = destination.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ComposeUpApp(
    modifier: Modifier = Modifier,
) {
    var shouldShowOnboarding by rememberSaveable { mutableStateOf(true) }
    Surface(modifier) {
        if (shouldShowOnboarding) {
            OnboardingScreen { shouldShowOnboarding = false }
        } else {
            Greetings()
        }
    }
}

@Composable
private fun Greetings(
    modifier: Modifier = Modifier,
    names: List<String> = List(1000) { "$it" }
) {
    LazyColumn(modifier = modifier.padding(vertical = 4.dp)) {
        items(items = names) { name ->
            Greeting(name = name)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        CardContent(name)
    }
}

@Composable
private fun CardContent(name: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(24.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .weight(1f)
        ) {
            Text(
                text = "Hello",
            )

            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                )
            )
            if (expanded) {
                Text(
                    text = ("Composem ipsum color sit lazy, " +
                            "padding theme elit, sed do bouncy. ").repeat(4),
                )
            }
        }
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = if (expanded) Filled.ExpandLess else Filled.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.show_more)
                } else {
                    stringResource(R.string.show_less)
                }
            )
        }
    }
}

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, onContinueClicked: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to the Basics Codelab!")
        Button(
            modifier = Modifier.padding(vertical = 24.dp),
            onClick = onContinueClicked
        ) {
            Text("Continue")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextShow(modifier: Modifier = Modifier) {
    Text(
        text = "Hello Compose",
        color = Color.Red,
        style = TextStyle(color = Color.Blue),
        modifier = modifier
    )
}


@Preview(showBackground = true, widthDp = 320, heightDp = 320)
@Composable
fun OnboardingPreview() {
    ComposeUpTheme {
        OnboardingScreen { }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GreetingPreview() {
    ComposeUpTheme {
        Greetings()
    }
}

@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
fun ComposeUpAppPreview() {
    ComposeUpTheme {
        ComposeUpApp()
    }
}

@Preview
@Composable
fun MyView() {
    Box {
        var imageHeightPx by remember { mutableIntStateOf(0) }

        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = "I'm above the text",
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    imageHeightPx = size.height
                }
        )

        Text(
            text = "I'm below the image",
            modifier = Modifier.padding(
                top = with(LocalDensity.current) { imageHeightPx.toDp() }
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun ExposedDropdownMenuSample() {
    // State to track the expanded state and selected option
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf("Option 1") }

// List of options for the dropdown menu
    val options = listOf("Option 1", "Option 2", "Option 3", "Option 4", "Option 5")

    Surface(
        modifier = Modifier.width(400.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {

                TextField(
                    value = selectedOption,
                    onValueChange = {},
                    readOnly = true,
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Blue)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                selectedOption = option
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}
