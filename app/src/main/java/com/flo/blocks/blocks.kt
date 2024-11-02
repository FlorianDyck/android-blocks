package com.flo.blocks

import androidx.compose.ui.unit.IntOffset
import java.util.ArrayList
import kotlin.math.max


data class Brick(val width: Int, val height: Int, val positions: BooleanArray) {
    init {
        assert(width * height == positions.size)
    }

    fun possiblyPlaceablePositions(): List<OffsetBrick> =
        (0..BOARD_SIZE - height).flatMap { y -> (0..BOARD_SIZE - width).map { x -> this.offset(IntOffset(x, y)) } }

    fun getPosition(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && positions[x + y * width]

    operator fun plus(other: Brick): Brick {
        val w = max(width, other.width)
        val h = max(height, other.height)
        return Brick(w, h, BooleanArray(w * h) {
            val x = it % w
            val y = it / w
            getPosition(x, y) || other.getPosition(x, y)
        })
    }

    fun rotate(): Brick = Brick(height, width, BooleanArray(width * height) {
        val x = it % height
        val y = it / height
        getPosition(y, height - x - 1)
    })

    fun flipHorizonally(): Brick = Brick(width, height, BooleanArray(width * height) {
        val x = it % width
        val y = it / width
        getPosition(width - x - 1, y)
    })

    fun flipVertically(): Brick = Brick(width, height, BooleanArray(width * height) {
        val x = it % width
        val y = it / width
        getPosition(x, height - y - 1)
    })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Brick

        if (width != other.width) return false
        if (height != other.height) return false
        if (!positions.contentEquals(other.positions)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + positions.contentHashCode()
        return result
    }

    override fun toString(): String {
        var result = ""
        for (y in 0 until height) {
            for (x in 0 until width) {
                result += if (getPosition(x, y)) "X" else " "
            }
            result += "\n"
        }
        return result.dropLast(1)
    }

    fun positionList(): List<IntOffset> {
        return (0 until height)
            .flatMap { y -> (0 until width).map { x -> IntOffset(x, y) } }
            .filter { getPosition(it.x, it.y) }
    }

    fun offset(offset: IntOffset) = OffsetBrick(offset, this)
}

fun rect(x0: Int, y0: Int, x1: Int, y1: Int): Brick =
    Brick(x1 + 1, y1 + 1, BooleanArray((x1 + 1) * (y1 + 1)) {
        val width = x1 + 1
        val x = it % width
        val y = it / width
        x in x0..x1 && y in y0..y1
    })

fun field(x: Int, y: Int): Brick = rect(x, y, x, y)

fun buildBlocks(): List<Brick> {
    val result = ArrayList<Brick>()

    fun addRotations(brick: Brick) {
        var rotated = brick
        do {
            result.add(rotated)
            rotated = rotated.rotate()
        } while (rotated != brick)
    }

    fun addAllVersions(brick: Brick) {
        addRotations(brick)
        val flipped = brick.flipVertically()
        if (flipped !in result) addRotations(flipped)
    }

    for (i in 0..4) addAllVersions(rect(0, 0, i, 0)) // 1*i
    for (i in 1..2) addAllVersions(rect(0, 0, i, i)) // i*i
    for (i in 1..2) addAllVersions(rect(0, 0, i, 0) + field(0, 1)) // iL2

    addAllVersions(rect(0, 0, 2, 0) + rect(0, 0, 0, 2)) // 3L3
    addAllVersions(rect(0, 0, 2, 0) + field(1, 1)) // T
    addAllVersions(rect(0, 0, 1, 0) + rect(1, 1, 2, 1)) // Z

    return result
}

val BRICKS = buildBlocks()

data class OffsetBrick(val offset: IntOffset, val brick: Brick) {
    private fun getXMin() = offset.x
    private fun getXMax() = offset.x + brick.width - 1
    private fun getYMin() = offset.y
    private fun getYMax() = offset.y + brick.height - 1
    private fun onBoard(): Boolean {
        return 0 <= offset.x && offset.x + brick.width <= BOARD_SIZE &&
                0 <= offset.y && offset.y + brick.height <= BOARD_SIZE
    }
    fun onBoard(board: BooleanArray): Boolean {
        return onBoard() && positionList().all { !board[it.x + it.y * BOARD_SIZE] }
    }
    fun onBoard(board: Array<BlockColor>): Boolean {
        return onBoard() && positionList().all { board[it.x + it.y * BOARD_SIZE].free() }
    }
    fun positionList(): List<IntOffset> {
        return brick.positionList().map { it + offset }
    }
    fun getPosition(x: Int, y: Int): Boolean {
        return brick.getPosition(x - offset.x, y - offset.y)
    }
    fun rows(): IntRange = getXMin()..getXMax()
    fun lines(): IntRange = getYMin()..getYMax()
}