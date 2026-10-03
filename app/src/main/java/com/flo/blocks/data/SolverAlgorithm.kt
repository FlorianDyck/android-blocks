package com.flo.blocks.data

/** Search policy used for hints and automatic move computation. */
enum class SolverAlgorithm {
    AndroidCurrent,
    AndroidGreedy,
    NativeGreedy,
    NativeBeam128,
    NativeBeam512,
    NativeExhaustive,
    NativePattern,
    NativeSymmetric;

    companion object {
        fun fromStoredValue(value: String?): SolverAlgorithm =
            entries.firstOrNull { it.name == value } ?: NativeBeam512
    }
}
