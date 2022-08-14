package starter
import screeps.api.*
import kotlin.math.max

typealias bodyArray = Array<BodyPartConstant>

val BASIC_CREEP: bodyArray = arrayOf(WORK, CARRY, MOVE)
val BASIC_WORKER: bodyArray = arrayOf(WORK, WORK, CARRY, MOVE)
val BASIC_OFF_ROAD: bodyArray = arrayOf(MOVE, CARRY, MOVE, CARRY, MOVE, MOVE)
val BASIC_CARRIER: bodyArray = arrayOf(CARRY, MOVE, CARRY, MOVE, CARRY, MOVE)

fun bestWorker(maxEnergy: Int): bodyArray {
    if (maxEnergy < 200) return BASIC_CREEP
    val maxBigParts = maxEnergy / 100
    return Array(maxBigParts+1) { i -> when {
        i < maxBigParts-1 -> WORK
        i == maxBigParts-1 -> CARRY
        else -> MOVE
    }}
}

fun bestOffRoad(maxEnergy: Int): bodyArray {
    val maxSmallParts = maxEnergy / 50
    return Array(maxSmallParts) { i -> when (i%3) {
        1 -> CARRY
        else -> MOVE
    }}
}


fun bestCarrier(maxEnergy: Int): bodyArray {
    val maxSmallParts = maxEnergy / 50
    return Array(maxSmallParts) { i -> when (i%2) {
        1 -> CARRY
        else -> MOVE
    }}
}

fun bestMultipurpose(maxEnergy: Int): bodyArray {
    if (maxEnergy < 200) return BASIC_CREEP
    val maxModuleParts = (maxEnergy / 300) * 5
    val maxSmallParts = max(0, (maxEnergy % 300) / 50 - 1)
    return Array(maxModuleParts + maxSmallParts) { i -> when (i%5) {
        0 -> WORK
        1 -> CARRY
        else -> MOVE
    }}
}

