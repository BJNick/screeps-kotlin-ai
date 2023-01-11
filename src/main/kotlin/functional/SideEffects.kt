package functional

import screeps.api.structures.StructureSpawn


/** execute - side effect function */
fun executeSpawn(spawn: StructureSpawn, command: SpawnCommand) {
    spawn.spawnCreep(command.body, command.name, command.spawnOptions)
}



