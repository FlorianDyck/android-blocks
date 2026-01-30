package com.flo.blocks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flo.blocks.data.AchievementFilter
import com.flo.blocks.game.BlockColor
import com.flo.blocks.game.Brick
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.ColoredBrick

@Composable
fun SelectNumber(description: String, initialValue: Int, set: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        var value: Int? by remember { mutableStateOf(initialValue) }
        Text(
                description,
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
        )
        TextField(
                value?.toString() ?: "",
                {
                    value = it.toIntOrNull()
                    if (value != null) {
                        set(value!!)
                    }
                },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
fun SelectOption(
        description: String,
        value: Boolean,
        enabled: Boolean = true,
        set: (Boolean) -> Unit
) {
    Row(
            (if (enabled) Modifier.clickable { set(!value) } else Modifier).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
    ) {
        val targetAlpha = if (enabled) 1f else .5f
        val animatedAlpha by animateFloatAsState(targetAlpha, label = "alpha")
        Text(
                description,
                Modifier.alpha(animatedAlpha).weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
        )
        Box(Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.CenterEnd) {
            Checkbox(value, { set(it) }, enabled = enabled)
        }
    }
}

@Composable
fun <T> SelectPossibleValue(
        description: String,
        value: T,
        possibleValues: Iterable<T>,
        label: @Composable (T) -> String = { it.toString() },
        set: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
            Modifier.fillMaxWidth().clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                description,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
        )
        Row(Modifier.weight(1f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                    label(value),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Icon(
                    Icons.Filled.KeyboardArrowDown,
                    stringResource(R.string.preview_select),
                    tint = MaterialTheme.colorScheme.onBackground
            )
            DropdownMenu(expanded, { expanded = false }) {
                for (possibleValue in possibleValues) {
                    DropdownMenuItem(
                            text = { Text(label(possibleValue)) },
                            trailingIcon = {
                                if (possibleValue == value)
                                        Icon(
                                                Icons.Filled.Check,
                                                stringResource(R.string.preview_selected)
                                        )
                            },
                            onClick = {
                                expanded = false
                                set(possibleValue)
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun SizeOptions(board: ColoredBoard, width: MutableIntState, height: MutableIntState) {
    var sync by remember { mutableStateOf(board.width == board.height) }
    val inSync by remember { derivedStateOf { sync && width.intValue == height.intValue } }
    AnimatedContent(targetState = inSync, contentAlignment = Alignment.Center, label = "inSync") {
            targetState ->
        if (targetState) {
            SelectPossibleValue(stringResource(R.string.width_and_height), width.intValue, 5..15) {
                width.intValue = it
                height.intValue = it
            }
        } else {
            Column {
                SelectPossibleValue(stringResource(R.string.width), width.intValue, 5..15) {
                    width.intValue = it
                    if (sync) height.intValue = it
                }
                SelectPossibleValue(stringResource(R.string.height), height.intValue, 5..15) {
                    height.intValue = it
                    if (sync) width.intValue = it
                }
            }
        }
    }
    SelectOption(stringResource(R.string.square_board), sync) { sync = it }
    AnimatedVisibility(width.intValue * height.intValue > 64) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                    stringResource(
                            R.string.computation_warning,
                            width.intValue * height.intValue,
                            width.intValue,
                            height.intValue
                    ),
                    Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun NewGameOptions(
        currentBoard: ColoredBoard,
        onCancel: () -> Unit,
        onConfirm: (Int, Int) -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
        ) {
            Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                        text = stringResource(R.string.new_game_options_title),
                        style = MaterialTheme.typography.titleLarge
                )

                val width = remember { mutableIntStateOf(currentBoard.width) }
                val height = remember { mutableIntStateOf(currentBoard.height) }

                SizeOptions(currentBoard, width, height)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                            onClick = { onConfirm(width.intValue, height.intValue) },
                            modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.start_game)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Options(computeViewModel: GameViewModel, openAchievements: () -> Unit, close: () -> Unit) {

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(R.string.settings)) },
                        navigationIcon = {
                            IconButton(onClick = close) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.achievement_back)
                                )
                            }
                        }
                )
            }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            // Hoist state variables
            var computeEnabled by remember { mutableStateOf(computeViewModel.computeEnabled) }
            var undoEnabled by remember { mutableStateOf(computeViewModel.undoEnabled) }
            var showBackIfEnabled by remember {
                mutableStateOf(computeViewModel.showUndoIfEnabled.value)
            }
            var showNewGameButton by remember {
                mutableStateOf(computeViewModel.showNewGameButton.value)
            }
            var achievementShowMinimalist by remember {
                mutableStateOf(computeViewModel.achievementShowMinimalist)
            }
            var achievementShowComeAndGone by remember {
                mutableStateOf(computeViewModel.achievementShowComeAndGone)
            }
            var achievementShowNewRecord by remember {
                mutableStateOf(computeViewModel.achievementShowNewRecord)
            }
            var achievementShowClearedLines by remember {
                mutableStateOf(computeViewModel.achievementShowClearedLines)
            }
            var achievementShowAroundTheCorner by remember {
                mutableStateOf(computeViewModel.achievementShowAroundTheCorner)
            }
            var achievementAlpha by remember {
                mutableFloatStateOf(computeViewModel.achievementAlpha.value)
            }
            var showBestEval by remember { mutableStateOf(computeViewModel.showBestEval) }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Scrollable container for settings
                Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            stringResource(R.string.settings_gameplay),
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.titleLarge
                    )

                    val computeLabels =
                            mapOf(
                                    GameViewModel.ComputeEnabled.Auto to R.string.compute_auto,
                                    GameViewModel.ComputeEnabled.Button to R.string.compute_button,
                                    GameViewModel.ComputeEnabled.Hidden to R.string.compute_hidden
                            )
                    SelectPossibleValue(
                            stringResource(R.string.settings_computation),
                            computeEnabled,
                            GameViewModel.ComputeEnabled.entries,
                            label = { stringResource(computeLabels[it]!!) }
                    ) {
                        computeEnabled = it
                        computeViewModel.computeEnabled = it
                    }

                    val undoLabels =
                            mapOf(
                                    GameViewModel.UndoEnabled.Always to R.string.undo_always,
                                    GameViewModel.UndoEnabled.UnlessNewBlocks to
                                            R.string.undo_unless_new_blocks,
                                    GameViewModel.UndoEnabled.Never to R.string.undo_never
                            )
                    SelectPossibleValue(
                            stringResource(R.string.settings_allow_undo),
                            undoEnabled,
                            GameViewModel.UndoEnabled.entries,
                            label = { stringResource(undoLabels[it]!!) }
                    ) {
                        undoEnabled = it
                        computeViewModel.undoEnabled = it
                    }

                    SelectOption(
                            stringResource(R.string.settings_show_undo_button),
                            showBackIfEnabled,
                            undoEnabled != GameViewModel.UndoEnabled.Never
                    ) {
                        showBackIfEnabled = it
                        computeViewModel.showUndoIfEnabled.value = it
                    }
                    SelectOption(
                            stringResource(R.string.settings_show_new_game_button),
                            showNewGameButton
                    ) {
                        showNewGameButton = it
                        computeViewModel.showNewGameButton.value = it
                    }
                    SelectOption(stringResource(R.string.settings_show_best_eval), showBestEval) {
                        showBestEval = it
                        computeViewModel.showBestEval = it
                    }

                    Text(
                            stringResource(R.string.settings_achievements),
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.titleLarge
                    )

                    Button(
                            onClick = openAchievements,
                            modifier = Modifier.padding(bottom = 16.dp)
                    ) { Text(stringResource(R.string.settings_view_achievements)) }

                    val filterLabels =
                            mapOf(
                                    AchievementFilter.Always to R.string.filter_always,
                                    AchievementFilter.ExceptThin to R.string.filter_except_thin,
                                    AchievementFilter.Never to R.string.filter_never
                            )
                    SelectPossibleValue(
                            stringResource(R.string.settings_show_minimalist),
                            achievementShowMinimalist,
                            AchievementFilter.entries,
                            label = { stringResource(filterLabels[it]!!) }
                    ) {
                        achievementShowMinimalist = it
                        computeViewModel.achievementShowMinimalist = it
                    }
                    SelectPossibleValue(
                            stringResource(R.string.settings_show_come_and_gone),
                            achievementShowComeAndGone,
                            AchievementFilter.entries,
                            label = { stringResource(filterLabels[it]!!) }
                    ) {
                        achievementShowComeAndGone = it
                        computeViewModel.achievementShowComeAndGone = it
                    }
                    SelectOption(
                            stringResource(R.string.settings_show_new_record),
                            achievementShowNewRecord
                    ) {
                        achievementShowNewRecord = it
                        computeViewModel.achievementShowNewRecord = it
                    }
                    SelectOption(
                            stringResource(R.string.settings_show_cleared_lines),
                            achievementShowClearedLines
                    ) {
                        achievementShowClearedLines = it
                        computeViewModel.achievementShowClearedLines = it
                    }
                    SelectOption(
                            stringResource(R.string.settings_show_around_the_corner),
                            achievementShowAroundTheCorner
                    ) {
                        achievementShowAroundTheCorner = it
                        computeViewModel.achievementShowAroundTheCorner = it
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                stringResource(R.string.settings_transparency),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                        )
                        androidx.compose.material3.Slider(
                                value = 1f - achievementAlpha,
                                onValueChange = {
                                    val newAlpha = 1f - it
                                    achievementAlpha = newAlpha
                                    computeViewModel.setAchievementAlpha(newAlpha)
                                },
                                valueRange = 0f..0.9f,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        )
                        Text(
                                "${((1f - achievementAlpha) * 100).toInt()}%",
                                Modifier.width(50.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End
                        )
                    }
                    BoxWithConstraints(
                            Modifier.padding(16.dp)
                                    .height(100.dp)
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                    ) { OptionsPreviewBoard(maxWidth, maxHeight, achievementAlpha) }
                }
            }
        }
    }
}

@Composable
fun OptionsPreviewBoard(
        maxWidth: androidx.compose.ui.unit.Dp,
        maxHeight: androidx.compose.ui.unit.Dp,
        achievementAlpha: Float
) {
    val boardWidth = 8
    val boardHeight = 3
    val blockSize =
            if (maxWidth / boardWidth < maxHeight / boardHeight) maxWidth / boardWidth
            else maxHeight / boardHeight

    // Background Board Pattern
    val previewBoard = remember {
        ColoredBoard(boardWidth, boardHeight).apply {
            this[2, 0] = BlockColor.RED
            this[3, 0] = BlockColor.RED
            this[2, 1] = BlockColor.RED
            this[3, 1] = BlockColor.RED

            this[6, 2] = BlockColor.ORANGE
            this[7, 2] = BlockColor.ORANGE

            this[0, 1] = BlockColor.GREEN
            this[0, 2] = BlockColor.GREEN
        }
    }
    Column {
        for (y in 0 until boardHeight) {
            Row {
                for (x in 0 until boardWidth) {
                    Block(previewBoard[x, y].color, blockSize)
                }
            }
        }
    }

    // Achievement Notification Overlay
    val dummyAchievement = remember {
        GameViewModel.Achievement(
                brick = ColoredBrick(Brick(1, 1, booleanArrayOf(true)), BlockColor.BLUE),
                cleared = 4,
                isNewRecord = true,
                blockRemoved = false,
                isMinimalist = false,
                aroundTheCorner = false,
                largeCorner = false,
                hugeCorner = false,
                wideCorner = false,
                notEvenAround = false,
                largeWideCorner = false
        )
    }

    AchievementNotification(
            achievement = dummyAchievement,
            alpha = achievementAlpha,
            blockSize = 40.dp
    )
}
