package com.flo.blocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.GameState
import com.flo.blocks.ui.theme.BlocksTheme

class MainActivity : ComponentActivity() {


    private val computeViewModel by viewModels<GameViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return GameViewModel() as T
                }
            }
        }
    )

    @Composable
    fun NumberSetting(description: String, set: (Int) -> Unit) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var value: Int? by remember {
                mutableStateOf(8)
            }
            Text(
                description,
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            TextField(
                value?.toString() ?: "", {
                    value = it.toIntOrNull()
                    if (value != null) {
                        set(value!!)
                    }
                }, Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            BlocksTheme {
                NavHost(navController = navController, startDestination = "game") {
                    composable("game") {
                        Game(computeViewModel) { navController.navigate("settings") }
                    }
                    composable("settings") {
                        Box(
                            Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column {
                                var width by remember { mutableStateOf(8) }
                                NumberSetting("width") { width = it }
                                var height by remember { mutableStateOf(8) }
                                NumberSetting("height") { height = it }
                                Button({
                                    val board = computeViewModel.game.value.board
                                    computeViewModel.updateGameState(GameState(
                                        ColoredBoard(width, height)
                                    ))
                                    navController.popBackStack()
                                }, Modifier.align(Alignment.CenterHorizontally)) {
                                    Text(text = "Save")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BlocksTheme {
        Greeting("Android")
    }
}