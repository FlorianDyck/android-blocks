package com.flo.blocks

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.HorizontalOrVertical
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.ColoredBrick
import com.flo.blocks.game.GameState
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun Block(color: Color, size: Dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size).padding(1f.dp)) {
        Box(
                Modifier.clip(RoundedCornerShape(5)).background(color).fillMaxSize(),
                contentAlignment = Alignment.Center
        ) { content() }
    }
}

fun vibrateCallback(context: Context): () -> Unit {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        return {
            vibratorManager.cancel()
            vibratorManager.vibrate(CombinedVibration.createParallel(effect))
        }
    } else {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            return {
                vibrator.cancel()
                vibrator.vibrate(effect)
            }
        } else {
            return {
                vibrator.cancel()
                @Suppress("DEPRECATION") vibrator.vibrate(100)
            }
        }
    }
}

@Composable
fun Brick(brick: ColoredBrick, blockSize: Dp) {
    Column {
        for (y in 0 until brick.brick.height) {
            Row {
                for (x in 0 until brick.brick.width) {
                    if (brick.brick.getPosition(x, y)) {
                        Block(brick.color.color, blockSize)
                    } else {
                        Spacer(modifier = Modifier.size(blockSize))
                    }
                }
            }
        }
    }
}

@Composable
fun computeLayout(board: ColoredBoard): Pair<Boolean, Int> {
    val size = LocalWindowInfo.current.containerSize
    val vertical = size.height >= size.width

    val boardSize = max(board.width, board.height)
    val blockSize =
            min(
                    max(size.width, size.height) / (boardSize + 12),
                    min(size.width, size.height) / max(boardSize + 1, 11)
            )
    return Pair(vertical, blockSize)
}

@Composable
fun AlignInDirection(
        vertical: Boolean,
        arrangement: HorizontalOrVertical,
        content: @Composable () -> Unit
) {
    if (vertical) {
        Column(
                verticalArrangement = arrangement,
                horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    } else {
        Row(horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
fun Game(gameViewModel: GameViewModel, backProgress: Float, openSettings: () -> Unit) {
    val game by gameViewModel.game.asStateFlow().collectAsState()
    val (vertical, blockDensity) = computeLayout(game.board)
    val blockSize = blockDensity / LocalDensity.current.density

    val lastGameState: GameState? by gameViewModel.lastGameState.asStateFlow().collectAsState()

    val suggestion by gameViewModel.nextMove.asStateFlow().collectAsState()
    //    val computation by computeViewModel.currentMove.asStateFlow().collectAsState()
    val computationProgress by gameViewModel.progress.asStateFlow().collectAsState()

    var achievementMessage by remember { mutableStateOf<GameViewModel.Achievement?>(null) }
    val highscore by gameViewModel.highscore.collectAsState()

    LaunchedEffect(Unit) {
        gameViewModel.achievementEvents.collectLatest { message ->
            achievementMessage = message
            delay(3000)
            achievementMessage = null
        }
    }

    val lost = game.lost()

    var offset: Pair<Int, Offset>? by remember { mutableStateOf(null) }

    var boardPosition: Offset by remember { mutableStateOf(Offset.Zero) }
    var blockPosition: Offset? by remember { mutableStateOf(null) }

    val hovering by
            remember(game, blockDensity) {
        derivedStateOf {
            blockPosition?.let {
                game.bricks[offset!!.first]?.brick?.offset(
                        ((it - boardPosition) / blockDensity.toFloat()).round()

                        )
                    }
                }
            }
    val selected by
            remember(game, hovering) {
        derivedStateOf { hovering?.let { game.board.canPlace(it) } ?: false }
    }

    @Composable
    fun Board(board: ColoredBoard, blockSize: Dp) {
        Column(
                Modifier.onGloballyPositioned { coordinates ->
                    boardPosition = coordinates.positionInRoot()
                }
        ) {
            for (y in 0 until board.height) {
                Row {
                    for (x in 0 until board.width) {
                        val color =
                                if (suggestion?.getPosition(x, y) == true ||
                                                (selected && hovering!!.getPosition(x, y))
                                )
                                        Color.Gray
                                else board[x, y].color
                        Block(color, blockSize) // { Text("$x,$y") }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Blocks(i: Int, brick: ColoredBrick?) {
        val density = LocalDensity.current.density
        val vibrate = vibrateCallback(LocalContext.current)
        Box(
                if (game.bricks[i] == null) {
                            Modifier
                        } else {
                            Modifier.zIndex(if (offset?.first == i) 1f else 0f)
                                    .offset {
                                        if (offset?.first == i) {
                                            offset!!.second.round()
                                        } else {
                                            IntOffset.Zero
                                        }
                                    }
                                    .draggable2D(
                                            state =
                                                    rememberDraggable2DState { delta ->
                                                        if (offset?.first == i)
                                                                offset =
                                                                        Pair(
                                                                                i,
                                                                                offset!!.second +
                                                                                        delta
                                                                        )
                                                    },
                                            onDragStarted = {
                                                if (offset == null)
                                                        offset =
                                                                Pair(
                                                                        i,
                                                                        Offset(
                                                                                0f,
                                                                                it.y -
                                                                                        (2 +
                                                                                                5 +
                                                                                                game.bricks[
                                                                                                                i]!!
                                                                                                        .brick
                                                                                                        .height) *
                                                                                                blockSize *
                                                                                                density /
                                                                                                2
                                                                        )
                                                                )
                                            },
                                            onDragStopped = {
                                                if (offset?.first == i) {
                                                    if (selected) {
                                                        val cleared =
                                                                gameViewModel.placeBrick(
                                                                        i,
                                                                        hovering!!.offset
                                                                )
                                                        if (cleared > 0) vibrate()
                                                        gameViewModel.canUndo()
                                                    }
                                                    offset = null
                                                    blockPosition = null
                                                }
                                            }
                                    )
                        }
                        .size((5 * blockSize).dp),
                contentAlignment = Alignment.Center
        ) {
            Box(
                    Modifier.onGloballyPositioned { coordinates ->
                        if (offset?.first == i) {
                            blockPosition = coordinates.positionInRoot()
                        }
                    }
            ) { brick?.let { Brick(it, blockSize.dp) } }
        }
    }

    val showNewGameOptions = remember { mutableStateOf(false) }

    if (showNewGameOptions.value) {
        NewGameOptions(
                game.board,
                onCancel = { showNewGameOptions.value = false },
                onConfirm = { width, height ->
                    gameViewModel.saveBoardSize(width, height)
                    gameViewModel.newGame(width, height)
                    showNewGameOptions.value = false
                }
        )
    }

    Scaffold(
            floatingActionButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val showUndo by gameViewModel.showUndo.collectAsState(false)
                    AnimatedVisibility(visible = showUndo) {
                        FloatingActionButton(onClick = { gameViewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.undo))
                        }
                    }
                    val showCompute by gameViewModel.showCompute.collectAsState()
                    AnimatedVisibility(visible = showCompute && suggestion == null) {
                        FloatingActionButton(
                                onClick = {
                                    gameViewModel.startComputation(
                                            game.bricks.filterNotNull().map { it.brick }
                                    )
                                },
                        ) { Icon(Icons.Filled.Search, stringResource(R.string.hint)) }
                    }
                    val showNewGameButton by gameViewModel.showNewGameButton.collectAsState()
                    AnimatedVisibility(visible = showNewGameButton) {
                        FloatingActionButton(onClick = { showNewGameOptions.value = true }) {
                            Icon(Icons.Filled.Add, stringResource(R.string.new_game))
                        }
                    }
                    FloatingActionButton(onClick = openSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    }
                }
            }
    ) { contentPadding ->
        Box(
                Modifier.background(MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                        .padding(contentPadding),
                contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                            text = stringResource(R.string.score, game.score),
                            style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                            text = stringResource(R.string.highscore, highscore),
                            style = MaterialTheme.typography.titleMedium
                    )
                }
                if (computationProgress < 1) {
                    LinearProgressIndicator(
                            progress = { computationProgress },
                            modifier = Modifier.width((game.board.width * blockSize).dp),
                    )
                }

                val spaced = Arrangement.spacedBy(blockSize.dp / 2)
                val Content =
                        @Composable
                        { game: GameState ->
                            AlignInDirection(vertical, spaced) {
                                Board(game.board, blockSize.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                            Modifier.height((5 * blockSize).dp)
                                                    .zIndex(if (offset?.first == 2) 0f else 1f),
                                            horizontalArrangement = spaced,
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Blocks(0, game.bricks[0])
                                        Blocks(1, game.bricks[1])
                                    }
                                    Row(
                                            Modifier.height((5 * blockSize).dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) { Blocks(2, game.bricks[2]) }
                                }
                            }
                        }
                Box {
                    Content(game)
                    if (backProgress > 0 && lastGameState != null) {
                        Box(
                                Modifier.alpha(backProgress)
                                        .background(MaterialTheme.colorScheme.background)
                        ) { Content(lastGameState!!) }
                    }
                }
            }
            if (lost) {
                Box(
                        Modifier.matchParentSize()
                                .alpha(0.5f)
                                .background(MaterialTheme.colorScheme.background)
                )
            }
            if (lost) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().zIndex(10f)
                ) {
                    Text(
                            text =
                                    if (game.score > highscore)
                                            stringResource(R.string.new_highscore)
                                    else stringResource(R.string.game_over),
                            style = MaterialTheme.typography.displayMedium,
                            color =
                                    if (game.score > highscore) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                            text = stringResource(R.string.score, game.score),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                            text = stringResource(R.string.highscore, highscore),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                                onClick = {
                                    gameViewModel.newGame()
                                    gameViewModel.canUndo()
                                }
                        ) { Text(stringResource(R.string.play_again)) }
                        Button(onClick = { showNewGameOptions.value = true }) {
                            Text(stringResource(R.string.customize))
                        }
                    }
                }
            }

            AnimatedVisibility(
                    visible = achievementMessage != null,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).zIndex(20f)
            ) {
                achievementMessage?.let { achievement ->
                    val achievementAlpha by gameViewModel.achievementAlpha.collectAsState()
                    AchievementNotification(achievement, achievementAlpha, blockSize.dp)
                }
            }
        }
    }
}

@Composable
fun AchievementNotification(
        achievement: GameViewModel.Achievement,
        alpha: Float,
        blockSize: Dp,
        modifier: Modifier = Modifier
) {
    val congratulations = stringArrayResource(R.array.congratulations)

    val parts = mutableListOf<String>()
    if (achievement.isMinimalist) parts.add(stringResource(R.string.notify_minimalist))
    else if (achievement.blockRemoved) parts.add(stringResource(R.string.notify_come_and_gone))

    if (achievement.isNewRecord)
            parts.add(stringResource(R.string.notify_new_record, achievement.cleared))

    // Corner Achievements Hierarchy
    if (achievement.largeWideCorner) {
        parts.add(stringResource(R.string.badge_large_wide_corner))
    }
    if (achievement.hugeCorner) {
        parts.add(stringResource(R.string.badge_huge_corner))
    }
    if (achievement.notEvenAround) {
        parts.add(stringResource(R.string.badge_not_even_around))
    }

    // Show LargeCorner only if not superseded by HugeCorner or LargeWideCorner
    if (achievement.largeCorner && !achievement.hugeCorner && !achievement.largeWideCorner) {
        parts.add(stringResource(R.string.badge_large_corner))
    }
    // Show WideCorner only if not superseded by LargeWideCorner
    if (achievement.wideCorner && !achievement.largeWideCorner) {
        parts.add(stringResource(R.string.badge_wide_corner))
    }

    // Show AroundTheCorner only if no specialized corner achievement is present
    val anySpecializedCorner =
            achievement.largeCorner ||
                    achievement.hugeCorner ||
                    achievement.wideCorner ||
                    achievement.notEvenAround ||
                    achievement.largeWideCorner
    if (achievement.aroundTheCorner && !anySpecializedCorner) {
        parts.add(stringResource(R.string.badge_around_the_corner))
    }
    if (parts.isEmpty()) parts.add(congratulations.random())
    if (achievement.cleared > 1)
            parts.add(stringResource(R.string.notify_cleared_lines, achievement.cleared))
    val message = remember(achievement) { parts.joinToString(" ") }

    Box(
            modifier.clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Brick(achievement.brick, (blockSize / 2))
            Text(
                    text = message,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
