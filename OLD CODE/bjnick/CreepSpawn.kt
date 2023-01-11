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



fun spawnCreeps(
    creeps: Array<Creep>,
    spawn: StructureSpawn
) {

    val capacity = spawn.room.energyCapacityAvailable

    val (role: Role, body: bodyArray) = when {

        creeps.count { it.memory.role == Role.SETTLER } < 3 -> Pair(Role.SETTLER, bestMultipurpose(capacity))

        creeps.count { it.memory.role == Role.CARRIER } < 3 -> Pair(Role.CARRIER, bestOffRoad(capacity))

        creeps.count { it.memory.role == Role.UPGRADER } < 2 -> Pair(Role.UPGRADER, bestWorker(capacity))

        spawn.room.find(FIND_CONSTRUCTION_SITES).isNotEmpty() &&
               creeps.count { it.memory.role == Role.BUILDER } < 2 -> Pair(Role.BUILDER, bestWorker(capacity))

        // TODO: Better harvesters

        else -> return
    }

    if (spawn.room.energyAvailable < body.sumOf { BODYPART_COST[it]!! }) {
        return
    }

    val newName = "${role.name}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role; this.distributesEnergy = role == Role.BUILDER || role == Role.UPGRADER }
    })

    when (code) {
        OK -> console.log("spawning $newName with body $body")
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> run { } // do nothing
        else -> console.log("unhandled error code $code")
    }
}