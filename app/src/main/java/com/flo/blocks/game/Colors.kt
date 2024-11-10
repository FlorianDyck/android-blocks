package com.flo.blocks.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.flo.blocks.ui.theme.BlocksTheme

fun color(hue: Float, sat: Float = 1f, lightness: Float = .5f): Color = Color.hsl(hue, sat, lightness)

enum class BlockColor(val color: Color){
    INVISIBLE(Color.Transparent),
    BACKGROUND(Color.LightGray),
    SELECTED(Color.DarkGray),
    RED(0f),
    GREEN(120f, lightness = .44f),
    BLUE(240f, lightness = .6f),
    ORANGE(30f),
    YELLOW(60f, lightness = .5f),
    CYAN(190f),
    VIOLET(285f)

    ;
    constructor(hue: Float, sat: Float = 1f, lightness: Float = .5f) : this(color(hue, sat, lightness))
    fun free() = this == INVISIBLE || this == BACKGROUND || this == SELECTED
    fun used() = !free()
}
val BLOCK_COLORS by lazy { BlockColor.entries.filter { it.used() } }

@Preview(showBackground = true)
@Composable
fun ColorPreview() {
    BlocksTheme {
        Column {
            for (color in BlockColor.entries) {
                Text("$color", Modifier.weight(1f).background(color.color))
            }
        }
    }
}