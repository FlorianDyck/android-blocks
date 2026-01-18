package com.flo.blocks

import com.flo.blocks.data.DataStoreSettingsRepository
import com.flo.blocks.data.SettingsRepository
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flo.blocks.ui.theme.BlocksTheme
import kotlin.math.pow

class MainActivity : ComponentActivity() {


    private val computeViewModel by viewModels<GameViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return GameViewModel(DataStoreSettingsRepository(application)) as T
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var backProgress by remember { mutableFloatStateOf(0f) }

            class MyOnBackPressedCallback : OnBackPressedCallback(computeViewModel.canUndo()) {
                override fun handleOnBackPressed() {
                    backProgress = 0f
                    isEnabled = computeViewModel.undo()
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    super.handleOnBackProgressed(backEvent)
                    backProgress = backEvent.progress.pow(1f / 3)
                }
            }

            val navController = rememberNavController()
            BlocksTheme {
                val myOnBackPressedCallback by remember {
                    mutableStateOf(MyOnBackPressedCallback())
                }
                NavHost(navController = navController, startDestination = "game") {
                    composable("game") {
                        val canUndo by computeViewModel.canUndo.collectAsState()
                        myOnBackPressedCallback.isEnabled = canUndo
                        Game(computeViewModel, backProgress) { navController.navigate("settings") }
                    }
                    composable("settings") {
                        myOnBackPressedCallback.isEnabled = false
                        Options(computeViewModel) {
                            if (navController.currentBackStackEntry != null) navController.popBackStack()
                        }
                    }
                }
                this.onBackPressedDispatcher.addCallback(this, myOnBackPressedCallback)
            }
        }
    }
}