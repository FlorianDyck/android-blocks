package com.flo.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flo.blocks.data.BlockAchievement
import com.flo.blocks.game.CANONICAL_BRICKS
import com.flo.blocks.game.Brick
import com.flo.blocks.game.ColoredBrick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsPage(gameViewModel: GameViewModel, onBack: () -> Unit) {
    var achievements by remember { mutableStateOf<List<BlockAchievement>>(emptyList()) }

    LaunchedEffect(Unit) {
        achievements = gameViewModel.getAllAchievements()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val achievementsMap = achievements.associateBy { it.brick }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(CANONICAL_BRICKS) { brick ->
                AchievementItem(
                    brick = brick,
                    maxLines = achievementsMap[brick]?.maxLinesCleared ?: 0
                )
            }
        }
    }
}

@Composable
fun AchievementItem(brick: Brick, maxLines: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                // Using a default color for achievements display
                Brick(ColoredBrick(brick, com.flo.blocks.game.BlockColor.BLUE), 10.dp)
            }
            
            Column {
                Text(
                    text = if (maxLines > 0) "Personal Best" else "No record yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "$maxLines lines cleared",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (maxLines > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
