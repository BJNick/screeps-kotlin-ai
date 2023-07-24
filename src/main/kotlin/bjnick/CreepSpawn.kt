package bjnick
import distributesEnergy
import role
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.utils.unsafe.jsObject
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
        2 -> CARRY
        else -> MOVE
    }}
}

fun bestOnRoad(maxEnergy: Int): bodyArray {
    val maxSmallParts = maxEnergy / 50
    return Array(maxSmallParts) { i -> when (i%3) {
        0 -> MOVE
        else -> CARRY
    }}
}

fun bestOffRoadWorker(maxEnergy: Int): bodyArray {
    val basicSetup = arrayOf(MOVE, MOVE, WORK, CARRY)
    val addon = bestOffRoad(maxEnergy-100-50-50-50)
    return basicSetup + addon
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

// Make a name based on tick and role
fun newName(role: String): String {
    // Ticks should be converted to letters: any, vowel, any
    val tick = Game.time % 26
    val tick2 = (Game.time / 26) % 5
    val tick3 = (Game.time / 26 / 5) % 26
    val tickName = "${"abcdefghijklmnopqrstuvwxyz"[tick3]}${"aeiou"[tick2]}${"abcdefghijklmnopqrstuvwxyz"[tick]}"
    return "${tickName.capitalize()}"
}

fun spawnCreeps(
    creeps: Array<Creep>,
    spawn: StructureSpawn
) {

    val capacity = spawn.room.energyCapacityAvailable

    val maxWorkParts = spawn.room.energyCapacityAvailable / 100 - 1
    val harvesterCount = spawn.room.optimalHarvesters(maxWorkParts)

    val (role: String, body: bodyArray) = when {

        //creeps.count { it.memory.role == Role.SETTLER } < harvesterCount -> Pair(Role.SETTLER, bestMultipurpose(capacity))
        creeps.count { it.memory.role == Role.HARVESTER && it.ticksToLive>50 } < harvesterCount -> Pair(Role.HARVESTER, bestWorker(capacity))

        creeps.count { it.memory.role == Role.CARRIER } < 4 -> Pair(Role.CARRIER, bestOnRoad(capacity)) // CHANGED FROM OFF ROAD

        creeps.count { it.memory.role == Role.UPGRADER } < 2 -> Pair(Role.UPGRADER, bestWorker(capacity))

        creeps.count { it.memory.role == Role.BUILDER } < 3 -> Pair(Role.BUILDER, bestWorker(capacity))

        creeps.count { it.memory.role == Role.REPAIRER } < 1 -> Pair(Role.REPAIRER, bestOffRoadWorker(capacity))

        creeps.count { it.memory.role == Role.PROSPECTOR } < 1 -> Pair(Role.PROSPECTOR, bestOffRoadWorker(capacity))

        spawn.room.find(FIND_CONSTRUCTION_SITES).isNotEmpty() &&
               creeps.count { it.memory.role == Role.BUILDER } < 2 -> Pair(Role.BUILDER, bestWorker(capacity))


        creeps.count { it.memory.role == Role.UPGRADER } < 3 -> Pair(Role.UPGRADER, bestWorker(capacity))

        creeps.count { it.memory.role == Role.CARRIER } < 6 -> Pair(Role.CARRIER, bestOnRoad(capacity)) // CHANGED FROM OFF ROAD

        creeps.count { it.memory.role == Role.REPAIRER } < 2 -> Pair(Role.REPAIRER, bestOffRoadWorker(capacity))

        // TODO: Better harvesters

        else -> return
    }

    if (spawn.room.energyAvailable < body.sumOf { BODYPART_COST[it]!! }) {
        return
    }

    val newName = newName(role)
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role; this.distributesEnergy = role == Role.BUILDER || role == Role.UPGRADER }
        directions = arrayOf(BOTTOM, LEFT, RIGHT) // specific optimal directions for current setup
    })

    when (code) {
        OK -> console.log("spawning $newName the ${role.lowercase()} with body $body")
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> run { } // do nothing
        else -> console.log("unhandled error code $code")
    }
}