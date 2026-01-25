package com.flo.blocks

import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.flo.blocks.data.AppDatabase
import com.flo.blocks.data.DataStoreSettingsRepository
import com.flo.blocks.data.GameRepository
import com.flo.blocks.ui.theme.BlocksTheme
import kotlin.math.pow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "game-database"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        setContent {
            val context = LocalContext.current
            val settingsRepository = DataStoreSettingsRepository(context)
            val gameRepository = GameRepository(db.gameDao(), db.blockAchievementDao())

            val gameViewModel: GameViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GameViewModel(settingsRepository, gameRepository) as T
                    }
                }
            )

            var backProgress by remember { mutableFloatStateOf(0f) }

            class MyOnBackPressedCallback : OnBackPressedCallback(gameViewModel.canUndo()) {
                override fun handleOnBackPressed() {
                    backProgress = 0f
                    isEnabled = gameViewModel.undo()
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
                        val canUndo by gameViewModel.canUndo.collectAsState()
                        myOnBackPressedCallback.isEnabled = canUndo
                        Game(gameViewModel, backProgress) { navController.navigate("settings") }
                    }
                    composable("settings") {
                        myOnBackPressedCallback.isEnabled = false
                        Options(gameViewModel, openAchievements = { navController.navigate("achievements") }) {
                            if (navController.currentBackStackEntry != null) navController.popBackStack()
                        }
                    }
                    composable("achievements") {
                        myOnBackPressedCallback.isEnabled = false
                        AchievementsPage(gameViewModel) {
                            if (navController.currentBackStackEntry != null) navController.popBackStack()
                        }
                    }
                }
                this.onBackPressedDispatcher.addCallback(this, myOnBackPressedCallback)
            }
        }
    }
}