package com.flo.blocks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.GameState

@Composable
fun SelectNumber(description: String, initialValue: Int, set: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var value: Int? by remember {
            mutableStateOf(initialValue)
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
            Modifier
                .alpha(animatedAlpha)
                .weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Box(
            Modifier
                .weight(1f)
                .padding(8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Checkbox(value, { set(it) }, enabled = enabled)
        }
    }
}

@Composable
fun <T> SelectPossibleValue(
    description: String,
    value: T,
    possibleValues: Iterable<T>,
    set: (T) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            description,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Row(
            Modifier
                .weight(1f)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                "select",
                tint = MaterialTheme.colorScheme.onBackground
            )
            DropdownMenu(expanded, { expanded = false }) {
                for (possibleValue in possibleValues) {
                    DropdownMenuItem(text = {
                        Text(possibleValue.toString())
                    }, trailingIcon = {
                        if (possibleValue == value) Icon(Icons.Filled.Check, "selected")
                    }, onClick = {
                        expanded = false
                        set(possibleValue)
                    })
                }
            }
        }
    }
}

@Composable
fun SizeOptions(board: ColoredBoard, width: MutableIntState, height: MutableIntState) {
    var sync by remember {
        mutableStateOf(board.width == board.height)
    }
    val inSync by remember {
        derivedStateOf {
            sync && width.intValue == height.intValue
        }
    }
    AnimatedContent(
        targetState = inSync,
        contentAlignment = Alignment.Center,
        label = "inSync"
    ) { targetState ->
        if (targetState) {
            SelectPossibleValue("Width and Height", width.intValue, 5..15) {
                width.intValue = it
                height.intValue = it
            }
        } else {
            Column {
                SelectPossibleValue("Width", width.intValue, 5..15) {
                    width.intValue = it
                    if (sync) height.intValue = it
                }
                SelectPossibleValue("Height", height.intValue, 5..15) {
                    height.intValue = it
                    if (sync) width.intValue = it
                }
            }
        }
    }
    SelectOption("Square Board", sync) { sync = it }
    AnimatedVisibility(width.intValue * height.intValue > 64) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                "Computations for boards with more than 64 positions are much slower and use more battery. " +
                "There are currently ${width.intValue * height.intValue} = ${width.intValue} (Width) * ${height.intValue} (Height) positions.",
                Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun Options(computeViewModel: GameViewModel, close: () -> Unit) {
    val game by computeViewModel.game.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gameplay", Modifier.padding(8.dp), style = MaterialTheme.typography.titleLarge)
                var computeEnabled by remember {
                    mutableStateOf(computeViewModel.computeEnabled)
                }
                SelectPossibleValue("Computation", computeEnabled, GameViewModel.ComputeEnabled.entries) {
                    computeEnabled = it
                }
                var undoEnabled by remember {
                    mutableStateOf(computeViewModel.undoEnabled)
                }
                SelectPossibleValue("Allow Undo", undoEnabled, GameViewModel.UndoEnabled.entries) {
                    undoEnabled = it
                }
                var showBackIfEnabled by remember {
                    mutableStateOf(computeViewModel.showUndoIfEnabled.value)
                }
                SelectOption(
                    "Show Undo Button",
                    showBackIfEnabled,
                    undoEnabled != GameViewModel.UndoEnabled.Never
                ) { showBackIfEnabled = it }

                val save = {
                    computeViewModel.computeEnabled = computeEnabled
                    computeViewModel.undoEnabled = undoEnabled
                    computeViewModel.showUndoIfEnabled.value = showBackIfEnabled
                }
                val anySettingChange by remember {
                    derivedStateOf {
                        computeViewModel.computeEnabled != computeEnabled ||
                        computeViewModel.undoEnabled != undoEnabled ||
                        computeViewModel.showUndoIfEnabled.value != showBackIfEnabled
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(close, Modifier.weight(1f)) {
                        Text(text = "Cancel")
                    }
                    Button({
                        save()
                        close()
                    }, Modifier.weight(1f), anySettingChange) {
                        Text(text = "Save")
                    }
                }

                Text("Board", Modifier.padding(8.dp), style = MaterialTheme.typography.titleLarge)
                val width = remember { mutableIntStateOf(game.board.width) }
                val height = remember { mutableIntStateOf(game.board.height) }
                val anyBoardChange by remember {
                    derivedStateOf {
                        width.intValue != game.board.width || height.intValue != game.board.height
                    }
                }
                SizeOptions(game.board, width, height)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(close, Modifier.weight(1f)) {
                        Text(text = "Cancel")
                    }
                    Button({
                        save()
                        computeViewModel.updateGameState(
                            GameState(
                                ColoredBoard(width.intValue, height.intValue)
                            )
                        )
                        close()
                    }, Modifier.weight(1f), anyBoardChange) {
                        Text(text = "Apply to new Game")
                    }
                }
            }
        }
    }
}