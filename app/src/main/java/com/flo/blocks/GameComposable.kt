package com.flo.blocks

import android.app.Activity
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.ColoredBrick
import com.flo.blocks.game.GameState
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min


@Composable
fun Block(color: Color, size: Dp, content: @Composable () -> Unit = {}) {
    Box(
        Modifier
            .size(size)
            .padding(1f.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(5))
                .background(color)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

fun vibrateCallback(context: Context): () -> Unit {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);
        {
            vibratorManager.cancel()
            vibratorManager.vibrate(CombinedVibration.createParallel(effect))
        }
    } else {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);
            {
                vibrator.cancel()
                vibrator.vibrate(effect)
            }
        } else {
            {
                vibrator.cancel()
                vibrator.vibrate(100)
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
    val configuration = (LocalContext.current as Activity).resources.configuration
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val vertical = height >= width

    val boardSize = max(board.width, board.height)
    val blockSize = min(
        max(width, height) / (boardSize + 12),
        min(width, height) / max(boardSize + 1, 11)
    )
    return Pair(vertical, blockSize)
}

@Composable
fun AlignInDirection(vertical: Boolean, arrangement: HorizontalOrVertical, content: @Composable () -> Unit) {
    if (vertical) {
        Column(
            verticalArrangement = arrangement,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    } else {
        Row(
            horizontalArrangement = arrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun Game(gameViewModel: GameViewModel, backProgress: Float, openSettings: () -> Unit) {
    val game by gameViewModel.game.asStateFlow().collectAsState()
    val (vertical, blockSize) = computeLayout(game.board)

    val lastGameState: GameState? by gameViewModel.lastGameState.asStateFlow().collectAsState()

    val suggestion by gameViewModel.nextMove.asStateFlow().collectAsState()
//    val computation by computeViewModel.currentMove.asStateFlow().collectAsState()
    val computationProgress by gameViewModel.progress.asStateFlow().collectAsState()

    val lost = game.lost()

    var offset: Pair<Int, Offset>? by remember { mutableStateOf(null) }

    var boardPosition: Offset by remember { mutableStateOf(Offset.Zero) }
    var blockPosition: Offset? by remember { mutableStateOf(null) }

    val blockDensity = LocalDensity.current.density * blockSize
    val hovering by remember {
        derivedStateOf {
            blockPosition?.let {
                game.bricks[offset!!.first]?.brick?.offset(((it - boardPosition) / blockDensity).round())
            }
        }
    }
    val selected by remember { derivedStateOf { hovering?.let { game.board.canPlace(it) } ?: false } }

    @Composable
    fun Board(board: ColoredBoard, blockSize: Dp) {
        Column(Modifier.onGloballyPositioned { coordinates ->
            boardPosition = coordinates.positionInRoot()
        }) {
            for (y in 0 until board.height) {
                Row {
                    for (x in 0 until board.width) {
                        val color = if (
                            suggestion?.getPosition(x, y) == true ||
                            (selected && hovering!!.getPosition(x, y))
                        ) Color.Gray else board[x, y].color
                        Block(color, blockSize) //{ Text("$x,$y") }
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
                Modifier
                    .zIndex(if (offset?.first == i) 1f else 0f)
                    .offset {
                        if (offset?.first == i) {
                            offset!!.second.round()
                        } else {
                            IntOffset.Zero
                        }
                    }
                    .draggable2D(
                        state = rememberDraggable2DState { delta ->
                            if (offset?.first == i) offset = Pair(i, offset!!.second + delta)
                        },
                        onDragStarted = {
                            if (offset == null) offset = Pair(
                                i, Offset(
                                    0f,
                                    it.y - (2 + 5 + game.bricks[i]!!.brick.height) * blockSize * density / 2
                                )
                            )
                        },
                        onDragStopped = {
                            if (offset?.first == i) {
                                if (selected) {
                                    val cleared = gameViewModel.placeBrick(i, hovering!!.offset)
                                    if (cleared > 0) vibrate()
                                    gameViewModel.canUndo()
                                }
                                offset = null
                                blockPosition = null
                            }
                        }
                    )
            }.size((5 * blockSize).dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    if (offset?.first == i) {
                        blockPosition = coordinates.positionInRoot()
                    }
                }
            ) {
                brick?.let { Brick(it, blockSize.dp) }
            }
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
                    FloatingActionButton(onClick = {
                        gameViewModel.undo()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "undo")
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
                    ) {
                        Icon(Icons.Filled.Search, "hint")
                    }
                }
                val showNewGameButton by gameViewModel.showNewGameButton.collectAsState()
                AnimatedVisibility(visible = showNewGameButton) {
                    FloatingActionButton(onClick = { showNewGameOptions.value = true }) {
                        Icon(Icons.Filled.Add, "new game")
                    }
                }
                FloatingActionButton(onClick = openSettings) {
                    Icon(Icons.Filled.Settings, "settings")
                }
            }
        }
    ) { contentPadding ->
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Score: ${game.score}", style = MaterialTheme.typography.titleMedium)
                if (computationProgress < 1) {
                    LinearProgressIndicator(
                        progress = { computationProgress },
                        modifier = Modifier.width((game.board.width * blockSize).dp),
                    )
                }

                val spaced = Arrangement.spacedBy(blockSize.dp / 2)
                val Content = @Composable { game: GameState ->
                    AlignInDirection(vertical, spaced) {
                        Board(game.board, blockSize.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                Modifier
                                    .height((5 * blockSize).dp)
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
                            ) {
                                Blocks(2, game.bricks[2])
                            }
                        }
                    }
                }
                Box {
                    Content(game)
                    if (backProgress > 0 && lastGameState != null) {
                        Box(
                            Modifier
                                .alpha(backProgress)
                                .background(MaterialTheme.colorScheme.background)) {
                            Content(lastGameState!!)
                        }
                    }
                }
            }
            if (lost) {
                Box(
                    Modifier
                        .matchParentSize()
                        .alpha(0.5f)
                        .background(MaterialTheme.colorScheme.background)
                )
            }
            if (lost) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                ) {
                    Text(
                        text = "Game Over",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Score: ${game.score}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = {
                            gameViewModel.newGame()
                            gameViewModel.canUndo()
                        }) {
                            Text("Play Again")
                        }
                        Button(onClick = { showNewGameOptions.value = true }) {
                            Text("Customize")
                        }
                    }
                }
            }
        }
    }
}