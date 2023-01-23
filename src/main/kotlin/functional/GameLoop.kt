package functional

import screeps.api.Game
import screeps.api.values

var DEBUG_MODE: Boolean = false


fun getCarryingCreeps(): List<CreepCarryingEnergy> {
    if (DEBUG_MODE) return listOf(CreepCarryingEnergy(_energyCarried = 100))
    return Game.creeps.values.toList().map { CreepCarryingEnergy(it) }
}


fun gameLoop() {

    val mainSpawn: Spawn = getSpawnList().firstOrNull() ?: throw IllegalStateException("No spawn found")

    val feedOrders = listOf( ConcreteFeedOrder(mainSpawn, 100) )

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
    val feedAssignments = assignCreepsToFeed(creeps, feedOrders)
    feedAssignments.forEach { (creep, order) ->
        executeFeedOrder(creep, order)  // SIDE EFFECT: sets creep memory
    }


}


