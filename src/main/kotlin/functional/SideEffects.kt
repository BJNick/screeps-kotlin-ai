package functional

import screeps.api.structures.StructureSpawn


/** Execute - side effect function */
fun executeSpawn(spawn: StructureSpawn, command: SpawnCommand) {
    spawn.spawnCreep(asBodyConstants(command.body), command.name, command.spawnOptions)
}



