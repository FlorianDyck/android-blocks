package com.flo.blocks.game

import android.content.Context

/** Display-only percentile maps exported by the C++ calibration pipeline. */
internal class ScoreCalibration private constructor(
    private val lower: Double,
    private val upper: Double,
    private val boundaries: DoubleArray,
    private val values: IntArray
) {
    fun score(evaluation: Float): Int {
        require(evaluation.isFinite()) { "Nonfinite board evaluation" }
        if (evaluation <= lower) return 0
        if (evaluation >= upper) return 100
        var low = 0
        var high = boundaries.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (boundaries[middle] <= evaluation.toDouble()) low = middle + 1 else high = middle
        }
        return values[low]
    }

    companion object {
        fun load(context: Context, width: Int, height: Int): ScoreCalibration? {
            val tokens = try {
                context.assets.open("calibration/${width}x$height.ranges")
                    .bufferedReader().use { it.readText().trim().split(Regex("\\s+")) }
            } catch (_: java.io.IOException) {
                return null
            }
            require(tokens.size >= 7 && tokens[0] == "BLOCKS_SCORE_RANGES" && tokens[1] == "1") {
                "Invalid score calibration header"
            }
            require(tokens[2].toInt() == width && tokens[3].toInt() == height) {
                "Score calibration dimensions do not match"
            }
            val lower = tokens[4].toDouble()
            val upper = tokens[5].toDouble()
            val boundaryCount = tokens[6].toInt()
            require(lower.isFinite() && upper.isFinite() && lower < upper &&
                boundaryCount in 0..100 && tokens.size == 7 + boundaryCount * 2 + 1) {
                "Invalid score calibration data"
            }
            val boundaries = DoubleArray(boundaryCount) { tokens[7 + it].toDouble() }
            require(boundaries.all { it.isFinite() } &&
                (1 until boundaries.size).all { boundaries[it - 1] < boundaries[it] }) {
                "Invalid score calibration boundaries"
            }
            val values = IntArray(boundaryCount + 1) { tokens[7 + boundaryCount + it].toInt() }
            require(values.all { it in 0..100 } &&
                (1 until values.size).all { values[it - 1] <= values[it] }) {
                "Invalid calibrated scores"
            }
            return ScoreCalibration(lower, upper, boundaries, values)
        }
    }
}
