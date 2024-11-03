package com.flo.blocks

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


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
fun Brick(color: Color, blockSize: Dp, brick: Brick) {
    Column {
        for (y in 0 until brick.height) {
            Row {
                for (x in 0 until brick.width) {
                    if (brick.getPosition(x, y)) {
                        Block(color, blockSize)
                    } else {
                        Spacer(modifier = Modifier.size(blockSize))
                    }
                }
            }
        }
    }
}

inline fun <reified T> randArray(size: Int, values: List<T>) =
    Array(size) { values[Random().nextInt(values.size)] }

fun randIntArray(size: Int, bound: Int, min: Int = 0) =
    IntArray(size) { min + Random().nextInt(bound - min) }

@Composable
fun Game(computeViewModel: ComputeViewModel) {
    val configuration = (LocalContext.current as Activity).resources.configuration
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val vertical = height >= width
    val blockSize = .9f * min(
        max(width, height) / (BOARD_SIZE + 11),
        min(width, height) / (BOARD_SIZE)
    )

    var board by rememberSaveable { mutableStateOf(Array(BOARD_SIZE * BOARD_SIZE) { BlockColor.BACKGROUND }) }
    var bricks by rememberSaveable { mutableStateOf(randIntArray(3, BRICKS.size)) }
    var colors by rememberSaveable { mutableStateOf(randArray(3, BLOCK_COLORS)) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var lastState: Triple<Array<BlockColor>, IntArray, Array<BlockColor>>? by rememberSaveable {
        mutableStateOf(
            null
        )
    }

    val suggestion by computeViewModel.nextMove.asStateFlow().collectAsState()
//    val computation by computeViewModel.currentMove.asStateFlow().collectAsState()
    val computationProgress by computeViewModel.progress.asStateFlow().collectAsState()

    val lost = (0..2).all { colors[it].free() || !canPlace(board, BRICKS[bricks[it]]) }

    var offset: Pair<Int, Offset>? by remember { mutableStateOf(null) }

    var boardPosition: Offset by remember { mutableStateOf(Offset.Zero) }
    var blockPosition: Offset? by remember { mutableStateOf(null) }

    val blockDensity = LocalDensity.current.density * blockSize
    val hovering by remember {
        derivedStateOf {
            blockPosition?.let {
                OffsetBrick(
                    ((it - boardPosition) / blockDensity).round(),
                    BRICKS[bricks[offset!!.first]]
                )
            }
        }
    }
    val selected by remember { derivedStateOf { hovering?.onBoard(board) ?: false } }

    var undo: () -> Unit = {}

    var backProgress by remember {
        mutableStateOf(0f)
    }

    class MyOnBackPressedCallback : OnBackPressedCallback(lastState != null) {
        override fun handleOnBackPressed() {
            backProgress = 0f
            undo()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            super.handleOnBackProgressed(backEvent)
            backProgress = backEvent.progress.pow(1f / 3)
        }
    }
    val myOnBackPressedCallback by remember {
        mutableStateOf(MyOnBackPressedCallback())
    }

    fun newBlocks() {
        bricks = randIntArray(3, BRICKS.size)
        colors = randArray(3, BLOCK_COLORS)
        lastState = null
        myOnBackPressedCallback.isEnabled = false
    }

    undo = {
        lastState?.let {
            board = it.first
            bricks = it.second
            colors = it.third
            lastState = null
            computeViewModel.stop()
            myOnBackPressedCallback.isEnabled = false
        }
    }

    val activity = (LocalContext.current as MainActivity)
    activity.onBackPressedDispatcher.addCallback(activity, myOnBackPressedCallback)

    @Composable
    fun Board(board: Array<BlockColor>, blockSize: Dp) {
        Column(Modifier.onGloballyPositioned { coordinates ->
            boardPosition = coordinates.positionInRoot()
        }) {
            for (y in 0 until BOARD_SIZE) {
                Row {
                    for (x in 0 until BOARD_SIZE) {
                        var color = if (
                            suggestion?.getPosition(x, y) == true ||
                            (selected && hovering!!.getPosition(x, y))
                        ) {
                            Color.Gray
                        } else {
                            board[x + y * BOARD_SIZE].color
                        }
                        if (backProgress > 0 && lastState != null) {
                            color = Color(
                                ColorUtils.blendARGB(
                                    color.toArgb(),
                                    lastState!!.first[x + y * BOARD_SIZE].color.toArgb(),
                                    backProgress
                                )
                            )
                        }
                        Block(color, blockSize) //{ Text("$x,$y") }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Blocks(i: Int) {
        val density = LocalDensity.current.density
        val vibrate = vibrateCallback(LocalContext.current)
        Box(
            if (colors[i].free()) {
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
                                    it.y - (2 + 5 + BRICKS[bricks[i]].height) * blockSize * density / 2
                                )
                            )
                        },
                        onDragStopped = {
                            if (offset?.first == i) {
                                if (selected) {
                                    lastState =
                                        Triple(board.clone(), bricks.clone(), colors.clone())
                                    val cleared = place(board, hovering!!, colors[i])
                                    computeViewModel.stop()
                                    if (cleared > 0) {
                                        vibrate()
                                        score += cleared
                                    }
                                    colors[i] = BlockColor.INVISIBLE
                                    colors = colors.clone()
                                    if (colors.all { it.free() }) {
                                        newBlocks()
                                    }
                                    myOnBackPressedCallback.isEnabled = lastState != null
                                }
                                offset = null
                                blockPosition = null
                            }
                        }
                    )
            }.size((5 * blockSize).dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier
                .onGloballyPositioned { coordinates ->
                    if (offset?.first == i) {
                        blockPosition = coordinates.positionInRoot()
                    }
                }
            ) {
                Brick(
                    if (backProgress > 0 && lastState != null) {
                        Color(
                            ColorUtils.blendARGB(
                                colors[i].color.toArgb(),
                                lastState!!.third[i].color.toArgb(),
                                backProgress
                            )
                        )
                    } else
                        colors[i].color, blockSize.dp, BRICKS[bricks[i]]
                )
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    if (lost) {
        LaunchedEffect(key1 = "lost") {
            val result = snackbarHostState.showSnackbar(
                "You lost",
                actionLabel = "play again",
                duration = SnackbarDuration.Indefinite
            )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    score = 0
                    board = Array(BOARD_SIZE * BOARD_SIZE) { BlockColor.BACKGROUND }
                    newBlocks()
                }

                SnackbarResult.Dismissed -> TODO()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = lastState != null) {
                    FloatingActionButton(onClick = undo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "undo")
                    }
                }
                AnimatedVisibility(visible = suggestion == null) {
                    FloatingActionButton(
                        onClick = {
                            computeViewModel.compute(
                                board,
                                bricks
                                    .map { i -> BRICKS[i] }
                                    .filterIndexed { index, _ -> colors[index].used() }
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Search, "hint")
                    }
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
//                Text(text = "Score: $score, Computing: $computation", style = MaterialTheme.typography.titleMedium)
//                Text("BoardOffset $boardPosition, BlockOffset $blockPosition")
//                Text(text = "backProgress: $backProgress")
                Text(text = "Score: $score", style = MaterialTheme.typography.titleMedium)
                if (computationProgress < 1) {
                    LinearProgressIndicator(
                        progress = { computationProgress },
                        modifier = Modifier.width((BOARD_SIZE * blockSize).dp),
                    )
                }

                val spaced = Arrangement.spacedBy(blockSize.dp / 2)
                if (vertical) {
                    Column(
                        verticalArrangement = spaced,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Board(board, blockSize.dp)
                        Row(
                            Modifier
                                .height((5 * blockSize).dp)
                                .zIndex(if (offset?.first == 2) 0f else 1f),
                            horizontalArrangement = spaced,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Blocks(0)
                            Blocks(1)
                        }
                        Row(
                            Modifier.height((5 * blockSize).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Blocks(2)
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = spaced,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Board(board, blockSize.dp)
                        Column(
                            Modifier
                                .width((5 * blockSize).dp)
                                .zIndex(if (offset?.first == 2) 0f else 1f),
                            verticalArrangement = spaced,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Blocks(0)
                            Blocks(1)
                        }
                        Column(
                            Modifier.width((5 * blockSize).dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Blocks(2)
                        }
                    }
                }
            }
        }
    }
}