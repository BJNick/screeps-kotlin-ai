package bjnick
import assignedRoom
import defendRoom
import distributesEnergy
import role
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.utils.unsafe.jsObject
import settlementRoom
import settlementRoom2
import kotlin.math.max
import kotlin.math.min

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

fun bestOnRoad(maxEnergy: Int, maxCapacity: Int = 10000): bodyArray {
    val maxSmallParts = min(maxEnergy / 50, maxCapacity / 50 * 3 / 2)
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

// TODO: Use as a harvester in the other room
fun bestFastWorker(maxEnergy: Int): bodyArray {
    val maxBigParts = (maxEnergy-50) / 200
    val remainingEnergy = maxEnergy - 50 - maxBigParts*200
    val maxSmallParts = remainingEnergy / 50
    return arrayOf(CARRY) + Array(maxBigParts*3) { i -> when (i%3) {
        0 -> MOVE
        1 -> MOVE
        else -> WORK
    }} + Array(maxSmallParts) { MOVE }
}

// TODO: Actually off-road is MOVE/WORK while on-road is MOVE/WORK/WORK
fun bestFastRoadWorker(maxEnergy: Int): bodyArray {
    val maxBigParts = (maxEnergy-50) / 150
    val remainingEnergy = maxEnergy - 50 - maxBigParts*150
    val maxSmallParts = remainingEnergy / 50
    return arrayOf(CARRY) + Array(maxBigParts*2) { i -> when (i%2) {
        0 -> MOVE
        else -> WORK
    }} + Array(maxSmallParts) { MOVE }
}

// Example: CARRY MOVE WORK WORK MOVE CARRY WORK MOVE CARRY CARRY MOVE CARRY MOVE
fun mixedRoadFastWorker(maxEnergy: Int, workParts: Int = 3): bodyArray {
    val maxDoubleWorkUnits = min(workParts/2,(maxEnergy) / 250)
    val maxSingleWorkUnits = min(workParts%2,(maxEnergy-maxDoubleWorkUnits*250) / 200)
    val remainingEnergy = maxEnergy  - maxDoubleWorkUnits*250 - maxSingleWorkUnits*200
    val maxDoubleCargoUnits = remainingEnergy / 150
    val maxSingleCargoUnits = remainingEnergy % 150 / 100
    val maxSmallParts = remainingEnergy % 150 % 100 / 50
    return /*arrayOf(CARRY) +*/ Array(maxDoubleWorkUnits*3) { i -> when (i%3) {
        0 -> MOVE
        1 -> WORK
        else -> WORK
    }} + Array(maxSingleWorkUnits*3) { i -> when (i%3) {
        0 -> MOVE
        1 -> CARRY
        else -> WORK
    }} + Array(maxDoubleCargoUnits*3) { i -> when (i%3) {
        0 -> MOVE
        1 -> CARRY
        else -> CARRY
    }} + Array(maxSingleCargoUnits*2) { i -> when (i%2) {
        0 -> MOVE
        else -> CARRY
    }} + Array(maxSmallParts) { MOVE }
}

val minFastHarvester5work: Array<BodyPartConstant> = arrayOf(
    MOVE, WORK, WORK, MOVE, WORK, WORK, MOVE, WORK, CARRY
)

// Off-road, Example: CARRY MOVE WORK MOVE WORK MOVE CARRY
fun mixedFastWorker(maxEnergy: Int, workParts: Int = 3): bodyArray {
    val maxSingleWorkUnits = min(workParts,(maxEnergy) / 200)
    val maxCargoUnits = (maxEnergy-maxSingleWorkUnits*200) / 100
    val maxSmallParts = (maxEnergy-maxSingleWorkUnits*200) % 100 / 50
    return /*arrayOf(CARRY) +*/ Array(maxSingleWorkUnits*2) { i -> when (i%2) {
        0 -> MOVE
        else -> WORK
    }} + Array(maxCargoUnits*2) { i -> when (i%2) {
        0 -> MOVE
        else -> CARRY
    }} + Array(maxSmallParts) { MOVE }
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

fun optimizedBuilder(maxEnergy: Int): bodyArray {
    // One CARRY part, up to 5 WORK parts, the rest MOVE parts
    val maxBigParts = (maxEnergy-50) / 150
    val remainingEnergy = maxEnergy - 50 - maxBigParts*150
    val maxSmallParts = remainingEnergy / 50
    return arrayOf(CARRY) + Array(maxBigParts*2) { i -> when (i%2) {
        0 -> MOVE
        else -> WORK
    }} + Array(maxSmallParts) { MOVE }
}

fun bestRangedFighter(maxEnergy: Int): bodyArray {
    val maxBigParts = maxEnergy / 200
    val remainingEnergy = maxEnergy - maxBigParts*200
    val maxSmallParts = remainingEnergy / 60
    return Array(maxSmallParts*2) { i -> when (i%2) {
        0 -> TOUGH
        else -> MOVE
    }} + Array(maxBigParts*2) { i -> when (i%2) {
        0 -> MOVE
        else -> RANGED_ATTACK
    }}
}

fun bestMeleeFighter(maxEnergy: Int): bodyArray {
    val maxBigParts = maxEnergy / (80+50)
    val remainingEnergy = maxEnergy - maxBigParts*(80+50)
    val maxSmallParts = remainingEnergy / 60
    return Array(maxSmallParts*2) { i -> when (i%2) {
        0 -> TOUGH
        else -> MOVE
    }} + Array(maxBigParts*2) { i -> when (i%2) {
        0 -> MOVE
        else -> ATTACK
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

class SpawnRequest(val role: String, val body: bodyArray, val modifyMemory: (CreepMemory) -> Unit = { })

fun spawnCreeps(
    creeps: Array<Creep>,
    spawn: StructureSpawn
) {


    fun limit(v: Int) = min(v, 850) // an arbitrary limit to avoid too many parts
    val capacity = limit(spawn.room.energyCapacityAvailable)
    val UNLIMITED = spawn.room.energyCapacityAvailable
    val currentlyAvailable = spawn.room.energyAvailable

    val maxWorkParts = spawn.room.energyCapacityAvailable / 100 - 1
    val harvesterCount = spawn.room.optimalHarvesters(maxWorkParts)

    // IF THERE ARE HOSTILES, ENTER EMERGENCY
    val hostilesPresent = Game.rooms.values.any { it.find(FIND_HOSTILE_CREEPS).isNotEmpty() }

    val safeModeActive = (Game.rooms[Memory.defendRoom]?.controller?.safeMode ?: 0) > 200

    val roomA = Memory.settlementRoom
    val roomB = Memory.settlementRoom2

    fun count(role: String, ticks: Int = 0, assignedRoom: String) =
        Game.creepCount(hasRole(role) and ticksToLiveOver(ticks) and hasAssignedRoom(assignedRoom))
    fun count(role: String, ticks: Int = 0) =
        Game.creepCount(hasRole(role) and ticksToLiveOver(ticks))


    val request: SpawnRequest = when {

        // EMERGENCY MILITARY
        hostilesPresent && creeps.count { it.memory.role == Role.BOUNCER && it.ticksToLive>100 } < 1 -> SpawnRequest(Role.BOUNCER, bestMeleeFighter(UNLIMITED))
        hostilesPresent && creeps.count { it.memory.role == Role.RANGER && it.ticksToLive>100 } < 1 -> SpawnRequest(Role.RANGER, bestRangedFighter(UNLIMITED))
        hostilesPresent && creeps.count { it.memory.role == Role.BOUNCER && it.ticksToLive>100 } < 2 -> SpawnRequest(Role.BOUNCER, bestMeleeFighter(UNLIMITED))
        hostilesPresent && creeps.count { it.memory.role == Role.RANGER && it.ticksToLive>100 } < 2 -> SpawnRequest(Role.RANGER, bestRangedFighter(UNLIMITED))
        hostilesPresent && creeps.count { it.memory.role == Role.BOUNCER && it.ticksToLive>100 } < 1 -> SpawnRequest(Role.BOUNCER, bestMeleeFighter(max(550, currentlyAvailable)))

        //creeps.count { it.memory.role == Role.SETTLER } < harvesterCount -> SpawnRequest(Role.SETTLER, bestMultipurpose(capacity))
        creeps.count { it.memory.role == Role.HARVESTER && it.ticksToLive>150 } < harvesterCount -> SpawnRequest(Role.HARVESTER, bestWorker(capacity))

        creeps.count { it.memory.role == Role.CARRIER } < 4 -> SpawnRequest(Role.CARRIER, bestOnRoad(capacity)) // CHANGED FROM OFF ROAD

        // MILITARY
        //!safeModeActive && creeps.count { it.memory.role == Role.RANGER && it.ticksToLive>100 } < 1 -> SpawnRequest(Role.RANGER, bestRangedFighter(capacity))
        //!safeModeActive && creeps.count { it.memory.role == Role.BOUNCER && it.ticksToLive>100 } < 1 -> SpawnRequest(Role.BOUNCER, bestMeleeFighter(capacity))
        ///

        creeps.count { it.memory.role == Role.UPGRADER } < 2 -> SpawnRequest(Role.UPGRADER, bestWorker(capacity))

        creeps.count { it.memory.role == Role.BUILDER } < 2 -> SpawnRequest(Role.BUILDER, mixedFastWorker(capacity))  // changed design

        creeps.count { it.memory.role == Role.REPAIRER } < 1 -> SpawnRequest(Role.REPAIRER, bestOffRoadWorker(capacity))

        creeps.count { it.memory.role == Role.ERRANDER } < 3 -> SpawnRequest(Role.ERRANDER, BASIC_CARRIER)

        // OFF WHILE SETTLING
        // creeps.count { it.memory.role == Role.PROSPECTOR } < 1 -> SpawnRequest(Role.PROSPECTOR, bestOffRoadWorker(capacity))

        spawn.room.find(FIND_CONSTRUCTION_SITES).isNotEmpty() &&
               creeps.count { it.memory.role == Role.BUILDER } < 2 -> SpawnRequest(Role.BUILDER, mixedFastWorker(capacity))


        //creeps.count { it.memory.role == Role.REPAIRER } < 2 -> SpawnRequest(Role.REPAIRER, bestOffRoadWorker(capacity))

        // Create more prospectors only if all creeps have more than 100 ticks to live
        // creeps.count { it.memory.role == Role.PROSPECTOR } < 4 && creeps.count { it.ticksToLive<100 } == 0 -> SpawnRequest(Role.PROSPECTOR, mixedFastWorker(capacity))

        ///// FOR THE OTHER ROOM
        count(Role.OUTER_HARVESTER, 100, roomA) < 2 ->
            SpawnRequest(Role.OUTER_HARVESTER, minFastHarvester5work) { it.assignedRoom = roomA }

        // Reduced since roads were built
        count(Role.SETTLER, 50, roomA) < 2 -> // different creep body
            SpawnRequest(Role.SETTLER, bestOffRoadWorker(capacity)) { it.assignedRoom = roomA }

        count(Role.CARAVAN, 0, roomA) < 1 ->
            SpawnRequest(Role.CARAVAN, bestOnRoad(UNLIMITED, 1000)) { it.assignedRoom = roomA }


        // THIS ROOM
        creeps.count { it.memory.role == Role.CARRIER } < 6 -> SpawnRequest(Role.CARRIER, bestOnRoad(capacity)) // CHANGED FROM OFF ROAD
        creeps.count { it.memory.role == Role.UPGRADER } < 3 -> SpawnRequest(Role.UPGRADER, bestWorker(capacity))

        // THE OTHER ROOM
        count(Role.OUTER_HARVESTER, 100, roomA) < 4 ->
            SpawnRequest(Role.OUTER_HARVESTER, minFastHarvester5work) { it.assignedRoom = roomA }

        ///// FOR THE EXTRA EXPANSION
        count(Role.OUTER_HARVESTER, 100, roomB) < 2 ->
            SpawnRequest(Role.OUTER_HARVESTER, minFastHarvester5work) { it.assignedRoom = roomB }

        count(Role.CARAVAN, 0, roomA) < 3 ->
            SpawnRequest(Role.CARAVAN, bestOnRoad(UNLIMITED, 1000)) { it.assignedRoom = roomA }

        // TOO MANY, CREATE CONGESTION
        //creeps.count { it.memory.role == Role.SETTLER } < 4 -> SpawnRequest(Role.SETTLER, bestOffRoadWorker(capacity))

        // EXTRA CARAVAN
        //creeps.count { it.memory.role == Role.CARAVAN } < 3 -> SpawnRequest(Role.CARAVAN, bestOnRoad(UNLIMITED))

        //creeps.count { it.memory.role == Role.CLAIMER } < 1 -> SpawnRequest(Role.CLAIMER, arrayOf(MOVE, MOVE, MOVE, MOVE, CLAIM))

        ///// FOR THE EXTRA EXPANSION
        count(Role.SETTLER, 0, roomB) < 2 ->
            SpawnRequest(Role.SETTLER, bestOffRoadWorker(capacity)) { it.assignedRoom = roomB }

        count(Role.CARAVAN, 0, roomB) < 1 -> // REDUCED TO ELIMINATE WAITING
            SpawnRequest(Role.CARAVAN, bestOnRoad(UNLIMITED, 1000)) { it.assignedRoom = roomB }

        creeps.count { it.memory.role == Role.UPGRADER } < 4 -> SpawnRequest(Role.UPGRADER, bestWorker(capacity))

        /// MINERAL AND MARKET
        count(Role.EXTRACTOR, 100) < 1 -> SpawnRequest(Role.EXTRACTOR, bestWorker(capacity))

        else -> return
    }

    if (spawn.room.energyAvailable < request.body.sumOf { BODYPART_COST[it]!! }) {
        return
    }

    val newMemory = jsObject<CreepMemory> { this.role = request.role; this.distributesEnergy = request.role == Role.BUILDER || request.role == Role.UPGRADER }
    request.modifyMemory(newMemory)

    val newName = newName(request.role)
    val code = spawn.spawnCreep(request.body, newName, options {
        memory = newMemory
        directions = arrayOf(BOTTOM, LEFT, RIGHT) // specific optimal directions for current setup
    })

    when (code) {
        OK -> console.log("spawning $newName the ${request.role.lowercase()} with body ${request.body}")
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> run { } // do nothing
        else -> console.log("unhandled error code $code")
    }
}