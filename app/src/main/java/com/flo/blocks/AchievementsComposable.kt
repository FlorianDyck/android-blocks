package com.flo.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flo.blocks.data.BlockAchievement
import com.flo.blocks.game.Brick
import com.flo.blocks.game.CANONICAL_BRICKS
import com.flo.blocks.game.ColoredBrick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsPage(gameViewModel: GameViewModel, onBack: () -> Unit) {
    var achievements by remember { mutableStateOf<List<BlockAchievement>>(emptyList()) }

    LaunchedEffect(Unit) { achievements = gameViewModel.getAllAchievements() }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(R.string.achievements_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription =
                                                stringResource(R.string.achievement_back)
                                )
                            }
                        }
                )
            }
    ) { padding ->
        val achievementsMap = achievements.associateBy { it.brick }

        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(CANONICAL_BRICKS) { brick ->
                val achievement = achievementsMap[brick]
                AchievementItem(brick = brick, achievement = achievement)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AchievementItem(brick: Brick, achievement: BlockAchievement?) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp
    ) {
        BoxWithConstraints {
            val isNarrow = maxWidth < 340.dp
            Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 16.dp else 24.dp)
            ) {
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    // Using a default color for achievements display
                    Brick(ColoredBrick(brick, com.flo.blocks.game.BlockColor.BLUE), 10.dp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    val maxLines = achievement?.maxLinesCleared ?: 0
                    Text(
                            text =
                                    if (maxLines > 0)
                                            stringResource(R.string.achievement_personal_best)
                                    else stringResource(R.string.achievement_no_record),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                            text =
                                    stringResource(
                                            R.string.achievement_lines_cleared,
                                            maxLines,
                                            brick.width + brick.height
                                    ),
                            style = MaterialTheme.typography.headlineSmall,
                            color =
                                    if (maxLines > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    if (isNarrow && achievement != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AchievementBadges(achievement, Modifier.fillMaxWidth())
                    }
                }

                if (!isNarrow && achievement != null) {
                    AchievementBadges(achievement, Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AchievementBadges(achievement: BlockAchievement, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
    ) {
        if (achievement.comeAndGone)
                Badge(
                        stringResource(R.string.badge_come_and_gone),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                )
        if (achievement.minimalist)
                Badge(
                        stringResource(R.string.badge_minimalist),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                )

        // Corner Achievements
        if (achievement.aroundTheCorner) {
            Badge(
                    stringResource(R.string.badge_around_the_corner),
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (achievement.largeWideCorner)
                Badge(
                        stringResource(R.string.badge_large_wide_corner),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
        if (achievement.hugeCorner)
                Badge(
                        stringResource(R.string.badge_huge_corner),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                )
        if (achievement.largeCorner && !achievement.largeWideCorner && !achievement.hugeCorner)
                Badge(
                        stringResource(R.string.badge_large_corner),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                )

        if (achievement.wideCorner && !achievement.largeWideCorner)
                Badge(
                        stringResource(R.string.badge_wide_corner),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
        if (achievement.notEvenAround)
                Badge(
                        stringResource(R.string.badge_not_even_around),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                )
    }
}

@Composable
private fun Badge(
        text: String,
        containerColor: androidx.compose.ui.graphics.Color,
        contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(bottom = 4.dp) // Gap
    ) {
        Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
        )
    }
}
