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

    /**
     * Calculates the minimum number of cells that must be cleared on the board to remove this brick.
     * A brick is removed when all its occupied cells are part of a cleared line (row or column).
     *
     * This method uses a brute-force search over all subsets of rows and columns that are occupied
     * by the brick to find the combination that covers all its cells while minimizing the total
     * area cleared on the board (using the inclusion-exclusion principle).
     */
    fun minCellsToClear(boardWidth: Int, boardHeight: Int): Int {
        // Find which local rows and columns actually contain brick cells
        val activeRows = (0 until height).filter { y -> (0 until width).any { x -> getPosition(x, y) } }
        val activeCols = (0 until width).filter { x -> (0 until height).any { y -> getPosition(x, y) } }

        var minCells = Int.MAX_VALUE
        
        // Use bitmaps to iterate through every possible combination of rows and columns
        val rowSubsets = 1 shl activeRows.size
        val colSubsets = 1 shl activeCols.size

        for (rMask in 0 until rowSubsets) {
            val selectedRows = mutableSetOf<Int>()
            for (i in activeRows.indices) {
                if (((rMask shr i) and 1) == 1) selectedRows.add(activeRows[i])
            }
            
            for (cMask in 0 until colSubsets) {
                val selectedCols = mutableSetOf<Int>()
                for (i in activeCols.indices) {
                    if (((cMask shr i) and 1) == 1) selectedCols.add(activeCols[i])
                }

                // Check if this specific combination of rows and columns "covers" the whole brick.
                // A brick is covered if every one of its cells is in one of the selected rows or columns.
                var covered = true
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        if (getPosition(x, y) && y !in selectedRows && x !in selectedCols) {
                            covered = false
                            break
                        }
                    }
                    if (!covered) break
                }

                if (covered) {
                    // Calculate total cells cleared on the board:
                    // Area = (Rows * BoardWidth) + (Cols * BoardHeight) - (Intersection)
                    val r = selectedRows.size
                    val c = selectedCols.size
                    val cells = r * boardWidth + c * boardHeight - r * c
                    if (cells < minCells) minCells = cells
                }
            }
        }
        return minCells
    }
}