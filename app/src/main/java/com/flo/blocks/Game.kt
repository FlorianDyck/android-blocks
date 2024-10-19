package com.flo.blocks

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import com.flo.blocks.ui.theme.BlocksTheme
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayList
import java.util.Random
import kotlin.math.max
import kotlin.math.min


const val BOARD_SIZE = 8
val INDEX_OFFSETS = arrayOf(
    Pair(-2.75f, 3f),
    Pair(2.75f, 3f),
    Pair(0f, 8.5f)
)

fun indexBrickOffset(
    vertical: Boolean,
    index: Int,
    brick: Brick
): Offset {
    val indexOffset = INDEX_OFFSETS[index]
    val shortDirection = .5f * (BOARD_SIZE) + indexOffset.first
    val longDirection = BOARD_SIZE + indexOffset.second

    val directedIndexOffset = if (vertical) {
        Offset(shortDirection, longDirection)
    } else {
        Offset(longDirection, shortDirection)
    }

    val brickOffset = Offset(-brick.width / 2f, -brick.height / 2f)
    return directedIndexOffset + brickOffset
}

fun offsetToPosition(vertical: Boolean, index: Int, offset: Offset, brick: Brick): OffsetBrick =
    OffsetBrick((offset + indexBrickOffset(vertical, index, brick)).round(), brick)

fun positionToOffset(vertical: Boolean, index: Int, position: IntOffset, brick: Brick): Offset =
    position.toOffset() - indexBrickOffset(vertical, index, brick)

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


fun place(board: IntArray, hovering: OffsetBrick, color: Int): Int {
    for (position in hovering.positionList()) {
        board[position.x + position.y * BOARD_SIZE] = color
    }
    val lines = ArrayList<Int>()
    val rows = ArrayList<Int>()
    for (line in hovering.lines()) {
        if ((0 until BOARD_SIZE).all { row -> board[row + BOARD_SIZE * line] != 0 }) {
            lines.add(line)
        }
    }
    for (row in hovering.rows()) {
        if ((0 until BOARD_SIZE).all { line -> board[row + BOARD_SIZE * line] != 0 }) {
            rows.add(row)
        }
    }
    for (line in lines) {
        for (row in 0 until BOARD_SIZE) {
            board[row + BOARD_SIZE * line] = 0
        }
    }
    for (row in rows) {
        for (line in 0 until BOARD_SIZE) {
            board[row + BOARD_SIZE * line] = 0
        }
    }
    return lines.size + rows.size
}

fun randIntArray(size: Int, bound: Int, min: Int = 0) =
    IntArray(size) { min + Random().nextInt(bound - min) }

fun canPlace(board: IntArray, brick: Brick): Boolean {
    for (y in 0..BOARD_SIZE - brick.height) {
        for (x in 0..BOARD_SIZE - brick.width) {
            if (OffsetBrick(IntOffset(x, y), brick).onBoard(board)) {
                return true
            }
        }
    }
    return false
}

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

    var board by rememberSaveable { mutableStateOf(IntArray(BOARD_SIZE * BOARD_SIZE) { 0 }) }
    var bricks by rememberSaveable { mutableStateOf(randIntArray(3, BRICKS.size)) }
    var colors by rememberSaveable { mutableStateOf(randIntArray(3, COLORS.size, 1)) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    val suggestion by computeViewModel.nextMove.asStateFlow().collectAsState()
//    val computation by computeViewModel.currentMove.asStateFlow().collectAsState()
    val computationProgress by computeViewModel.progress.asStateFlow().collectAsState()

    fun newBlocks() {
        bricks = randIntArray(3, BRICKS.size)
        colors = randIntArray(3, COLORS.size, 1)
    }

    if (colors.all { it == 0 }) {
        newBlocks()
    }
    computeViewModel.compute(
        board,
        bricks
            .map { i -> BRICKS[i] }
            .filterIndexed { index, _ -> colors[index] > 0 }
    )

    val lost = (0..2).all { colors[it] == 0 || !canPlace(board, BRICKS[bricks[it]]) }

    var offset: Pair<Int, Offset>? by remember { mutableStateOf(null) }

    val blockDensity = LocalDensity.current.density * blockSize
    val hovering by remember {
        derivedStateOf {
            offset?.let {
                val brick = BRICKS[bricks[it.first]]
                val ibo = indexBrickOffset(vertical, it.first, brick)
                OffsetBrick((it.second / blockDensity + ibo).round(), brick)
            }
        }
    }
    val selected by remember { derivedStateOf { hovering?.onBoard(board) ?: false } }

    @Composable
    fun Board(board: IntArray, blockSize: Dp) {
        Column {
            for (y in 0 until BOARD_SIZE) {
                Row {
                    for (x in 0 until BOARD_SIZE) {
                        Block(
                            if ( suggestion?.getPosition (x,y) == true || (selected && hovering!!.getPosition(x, y)) ) {
                                Color.Gray
                            } else {
                                COLORS[board[x + y * BOARD_SIZE]]
                            },
                            blockSize
                        ) //{ Text("$x,$y") }
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
            if (colors[i] == 0) {
                Modifier
            } else {
                Modifier
                    .zIndex(
                        if (offset?.first == i) {
                            1f
                        } else {
                            0f
                        }
                    )
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
                                    val cleared = place(board, hovering!!, colors[i])
                                    if (cleared > 0) {
                                        vibrate()
                                        score += cleared
                                    }
                                    colors[i] = 0
                                    colors = colors.clone()
                                }
                                offset = null
                            }
                        }
                    )
            }.size((5 * blockSize).dp),
            contentAlignment = Alignment.Center
        ) {
            Brick(COLORS[colors[i]], blockSize.dp, BRICKS[bricks[i]])
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
                    board = IntArray(BOARD_SIZE * BOARD_SIZE) { 0 }
                    newBlocks()
                }

                SnackbarResult.Dismissed -> TODO()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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
                                .zIndex(
                                    if (offset?.first == 2) {
                                        0f
                                    } else {
                                        1f
                                    }
                                ),
                            horizontalArrangement = spaced,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0..1) {
                                Blocks(i = i)
                            }
                        }
                        Row(
                            Modifier.height((5 * blockSize).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Blocks(i = 2)
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
                                .zIndex(
                                    if (offset?.first == 2) {
                                        0f
                                    } else {
                                        1f
                                    }
                                ),
                            verticalArrangement = spaced,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (i in 0..1) {
                                Blocks(i = i)
                            }
                        }
                        Column(
                            Modifier.width((5 * blockSize).dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Blocks(i = 2)
                        }
                    }
                }
            }
        }
    }
}