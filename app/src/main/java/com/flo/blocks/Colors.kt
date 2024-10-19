package com.flo.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.flo.blocks.ui.theme.BlocksTheme

fun color(hue: Float, sat: Float = 1f, lightness: Float = .5f): Color = Color.hsl(hue, sat, lightness)

val RED = color(0f)
val GREEN = color(120f, lightness = .44f)
val BLUE = color(240f, lightness = .6f)

val ORANGE = color(30f)
val YELLOW = color(60f, lightness = .5f)
val CYAN = color(190f)
val VIOLET = color(285f)

val COLORS = arrayOf(Color.LightGray, RED, GREEN, BLUE, YELLOW, CYAN, VIOLET, ORANGE)


@Preview(showBackground = true)
@Composable
fun ColorPreview() {
    BlocksTheme {
        Column {
            for (color in COLORS) {
                Text("$color", Modifier.weight(1f).background(color))
            }
        }
    }
}