package functional

import screeps.api.structures.*;

fun gameLoop() {

    val mainSpawn: StructureSpawn = getSpawnList().firstOrNull() ?: throw IllegalStateException("No spawn found")

    val feedOrders = { ConcreteFeedOrder(mainSpawn, 100) }


    // Obtain spawn commands
    // TODO...
    val spawnCommands = listOf<SpawnCommand>()

    // Execute spawn commands
    val spawnStructures = getSpawnList()
    val spawnAssignments = assignSpawns(spawnCommands, spawnStructures)

    spawnAssignments.forEach { (spawn, command) ->
        executeSpawn(spawn, command)  // SIDE EFFECT: spawns a creep
    }




}


