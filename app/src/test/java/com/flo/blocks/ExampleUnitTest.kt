package com.flo.blocks

import com.flo.blocks.game.BRICKS
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        for (block in BRICKS) {
            println(block)
            println()
        }
        assertEquals(4, 2 + 2)
    }
}