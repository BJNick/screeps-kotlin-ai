package functional

import collectData
import screeps.api.*

var DEBUG_MODE: Boolean = false

fun getCarryingCreeps(): List<CreepCarryingEnergy> {
    if (DEBUG_MODE) return listOf(CreepCarryingEnergy(energyCarried=100))
    return Game.creeps.values.toList().map { CreepCarryingEnergy(it) }
}


fun scrapeEnvironment(room: Room) {
    // Save the room tile data as well as sources and minerals locations
    // Save the room structure data as well as controller location
    // Save the buffer as a string
    val rawBuffer = room.getTerrain().getRawBuffer()
    // make . = empty, W = wall, X = swamp, S = source, M = mineral, C = controller
    // needs to be AND & with TERRAIN_MASK_WALL or TERRAIN_MASK_SWAMP
    val buffer = rawBuffer.map{
        if (it and TERRAIN_MASK_WALL.value > 0) "W"
        else if (it and TERRAIN_MASK_SWAMP.value > 0) "X"
        else "."
    }
    // Now add the sources and minerals
    val sources = room.find(FIND_SOURCES)
    val minerals = room.find(FIND_MINERALS)
    val controller = room.controller
    fun getLinearIndex(pos: RoomPosition): Int {
        return pos.y * 50 + pos.x
    }
    val bufferUpdated = buffer.mapIndexed { index, c ->
        when {
            sources.any { getLinearIndex(it.pos) == index } -> "S" // Source
            minerals.any { getLinearIndex(it.pos) == index } -> "M" // Mineral
            controller?.let { getLinearIndex(it.pos) == index } ?: false -> "C" // Controller
            else -> c
        }
    }
    // Send string to console, make sure to convert to characters first
    val bufferString = bufferUpdated.joinToString("")
    console.log(bufferString)
    room.memory.collectData = false // Don't collect data again
}


fun gameLoop() {

    val mainSpawn: Spawn = getSpawnList().firstOrNull() ?: throw IllegalStateException("No spawn found")

    if (mainSpawn.structure?.room?.memory?.collectData == true) {
        scrapeEnvironment(mainSpawn.structure.room)
    }

    val feedOrders = listOf( feedOrderFromStoreOwner(mainSpawn, 100) )

    val creeps = getCarryingCreeps()


    // Obtain spawn orders
    val spawnOrders = listOf(
        SpawnOrder(SpawnCommand(arrayOf(BodyPart.MOVE), "Miner", blankSpawnOptions()), 10),
        SpawnOrder(SpawnCommand(arrayOf(BodyPart.MOVE), "Mover", blankSpawnOptions()), 5)
    )

    // Sort by priority, convert to commands
    val spawnCommands = sortByPriority(spawnOrders).map { it.spawnCommand }.toList()

    // Execute spawn commands
    val spawns = getSpawnList()
    val spawnAssignments = assignSpawns(spawnCommands, spawns)

    spawnAssignments.forEach { (spawn, command) ->
        executeSpawn(spawn, command)  // SIDE EFFECT: spawns a creep
    }

    // Execute feed orders
    val feedAssignments = assignCreepsToFeed(creeps, feedOrders) // TODO Subtract already assigned
    feedAssignments.forEach { (creep, order) ->
        assignFeedOrder(creep, order)  // SIDE EFFECT: sets creep memory
        executeTransfer(creep, order)  // SIDE EFFECT: transfers energy to target (if possible)
    }

    // Execute collect orders
    val collectOrders = emptyList<CollectOrder>()

    val collectAssignments = assignCreepsToCollect(creeps, collectOrders) // TODO Subtract already assigned
    collectAssignments.forEach { (creep, order) ->
        assignCollectOrder(creep, order)  // SIDE EFFECT: sets creep memory
        executeCollection(creep, order)  // SIDE EFFECT: withdraws energy from target (if possible)
    }


}


