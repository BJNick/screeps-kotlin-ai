package functional

import screeps.api.*
import screeps.api.structures.*;
import kotlin.math.min

fun getSpawnList(): List<StructureSpawn> {
    // provided information, no cost
    return Game.spawns.values.toList()
}

fun getEnergyCapacityAvailable(room: Room): Int = room.energyCapacityAvailable
fun getEnergyAvailable(room: Room): Int = room.energyAvailable


fun bodyPartCost(bodyPartType: BodyPartConstant): Int = when (bodyPartType) {
    /*MOVE -> 50
    WORK -> 100
    CARRY -> 50
    ATTACK -> 80
    RANGED_ATTACK -> 150
    HEAL -> 250
    CLAIM -> 600
    TOUGH -> 10*/
    else -> 0
}


class SpawnCommand(val body: Array<BodyPartConstant>, val name: String, val spawnOptions: SpawnOptions) {

    fun getEnergyCost(): Int {
        return body.sumOf { bodyPartCost(it) }
    }

    fun countParts(bodyPartType: BodyPartConstant): Int {
        return body.count { it == bodyPartType }
    }

}


fun makeName(role: String, id: Int): String {
    return role + id
}


fun assignSpawns(
    spawnCommands: List<SpawnCommand>,
    spawnStructures: List<StructureSpawn>
): List<Pair<StructureSpawn, SpawnCommand>> {
    // find minimum length between the two lists
    val minListLength = min(spawnCommands.size, spawnStructures.size)
    // pair them up
    val spawnCommandList = spawnCommands.subList(0, minListLength)
    val spawnStructureList = spawnStructures.subList(0, minListLength)
    return spawnStructureList.zip(spawnCommandList)
}