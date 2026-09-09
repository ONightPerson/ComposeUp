package com.example.composeup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.example.composeup.datastore.SettingsScreen
import com.example.composeup.nestedscroll.NestedScrollHub
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

/** 可切换的演示页面。 */
private enum class Destination(val title: String, val icon: ImageVector) {
    Home("原示例 App", Filled.Home),
    NestedScroll("嵌套滑动进阶", Filled.SwapVert),
    Settings("DataStore 设置页", Filled.Settings),
}

/**
 * 根界面：点右下角 FAB 在几个演示页面之间循环切换。
 */
@Composable
private fun ComposeUpRoot() {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val all = Destination.entries
                    destination = all[(destination.ordinal + 1) % all.size]
                },
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.title,
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (destination) {
            Destination.Home -> ComposeUpApp(contentModifier)
            Destination.NestedScroll -> NestedScrollHub(contentModifier)
            Destination.Settings -> SettingsScreen(contentModifier)
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
