package com.flo.blocks.game

fun rect(x0: Int, y0: Int, x1: Int, y1: Int): Brick =
    Brick(x1 + 1, y1 + 1, BooleanArray((x1 + 1) * (y1 + 1)) {
        val width = x1 + 1
        val x = it % width
        val y = it / width
        x in x0..x1 && y in y0..y1
    })

fun field(x: Int, y: Int): Brick = rect(x, y, x, y)

private val canonicalMap = HashMap<Brick, Brick>()

val Brick.canonical: Brick
    get() = canonicalMap[this] ?: this

fun buildBricks(): Pair<List<Brick>, List<Brick>> {
    val result = ArrayList<Brick>()
    val canonicals = ArrayList<Brick>()
    canonicalMap.clear()

    fun addRotations(brick: Brick, canonical: Brick) {
        var rotated = brick
        do {
            if (rotated !in result) {
                result.add(rotated)
                canonicalMap[rotated] = canonical
            }
            rotated = rotated.rotate()
        } while (rotated != brick)
    }

    fun addAllVersions(brick: Brick) {
        canonicals.add(brick)
        addRotations(brick, brick)
        val flipped = brick.flipVertically()
        addRotations(flipped, brick)
    }

    for (i in 0..4) addAllVersions(rect(0, 0, i, 0)) // 1*i
    for (i in 1..2) addAllVersions(rect(0, 0, i, i)) // i*i
    for (i in 1..2) addAllVersions(rect(0, 0, i, 0) + field(0, 1)) // iL2

    addAllVersions(rect(0, 0, 2, 0) + rect(0, 0, 0, 2)) // 3L3
    addAllVersions(rect(0, 0, 2, 0) + field(1, 1)) // T
    addAllVersions(rect(0, 0, 1, 0) + rect(1, 1, 2, 1)) // Z

    return Pair(result, canonicals)
}

private val bricksPair = buildBricks()
val BRICKS = bricksPair.first
val CANONICAL_BRICKS = bricksPair.second