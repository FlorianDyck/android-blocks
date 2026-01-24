package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset
import kotlin.math.max


open class Brick(val width: Int, val height: Int, val positions: BooleanArray) {
    init {
        assert(width * height == positions.size)
    }

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

    fun flipHorizontally(): Brick = Brick(width, height, BooleanArray(width * height) {
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

    fun offsetsWithin(width: Int, height: Int): List<OffsetBrick> {
        return (0..height - this.height).flatMap { y -> (0..width - this.width).map { x -> offset(IntOffset(x, y)) } }
    }
}