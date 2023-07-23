package bjnick

import forceReassignSources
import numberOfCreeps
import role
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete

class ProgressState {
    companion object Factory {
        val carriersPresent: Boolean
            get() = Game.creeps.values.count { it.memory.role == Role.CARRIER } > 0
    }
}

fun gameLoop() {
    val mainSpawn: StructureSpawn = Game.spawns.values.firstOrNull() ?: return

    //delete memories of creeps that have passed away
    houseKeeping(Game.creeps)

    // just an example of how to use room memory
    mainSpawn.room.memory.numberOfCreeps = mainSpawn.room.find(FIND_CREEPS).count()

    //make sure we have at least some creeps
    spawnCreeps(Game.creeps.values, mainSpawn)

    for ((_, creep) in Game.creeps) {
        creep.executeRole()
    }

    mainSpawn.room.visualizeSources()

    if (Memory.forceReassignSources) {
        for ((creepName, _) in Memory.creeps) {
            unassignSource(creepName)
        }
        Memory.forceReassignSources = false
    }
}

private fun houseKeeping(creeps: Record<String, Creep>) {
    if (Game.creeps.isEmpty()) return  // this is needed because Memory.creeps is undefined

    for ((creepName, _) in Memory.creeps) {
        if (creeps[creepName] == null) {
            console.log("deleting obsolete memory entry for creep $creepName")
            unassignSource(creepName)
            delete(Memory.creeps[creepName])
        }
    }
}
