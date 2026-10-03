package com.flo.blocks

import com.flo.blocks.data.SolverAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Test

class SolverAlgorithmTest {
    @Test
    fun `stored algorithm names round trip`() {
        for (algorithm in SolverAlgorithm.entries) {
            assertEquals(algorithm, SolverAlgorithm.fromStoredValue(algorithm.name))
        }
    }

    @Test
    fun `missing or unknown algorithm defaults to Beam 512`() {
        assertEquals(SolverAlgorithm.NativeBeam512, SolverAlgorithm.fromStoredValue(null))
        assertEquals(SolverAlgorithm.NativeBeam512, SolverAlgorithm.fromStoredValue("FuturePolicy"))
    }
}
