package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset
import java.util.Random


data class ColoredBrick(val brick: Brick, val color: BlockColor) {
    fun offset(offset: IntOffset) = ColoredOffsetBrick(brick.offset(offset), color)
}

data class ColoredOffsetBrick(val brick: OffsetBrick, val color: BlockColor)

fun randomBrick(
    bricks: List<Brick> = BRICKS,
    colors: List<BlockColor> = BLOCK_COLORS
): ColoredBrick {
    return ColoredBrick(
        bricks[Random().nextInt(bricks.size)],
        colors[Random().nextInt(colors.size)]
    )
}