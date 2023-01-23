package functional

import screeps.api.*
import screeps.api.structures.*
import kotlin.math.min

/** Abstract representation of a creep body part */
enum class BodyPart {
    MOVE, WORK, CARRY, ATTACK, RANGED_ATTACK, HEAL, CLAIM, TOUGH
}

/** Returns the cost of a body part */
fun bodyPartCost(bodyPartType: BodyPart): Int = when (bodyPartType) {
    BodyPart.MOVE -> 50
    BodyPart.WORK -> 100
    BodyPart.CARRY -> 50
    BodyPart.ATTACK -> 80
    BodyPart.RANGED_ATTACK -> 150
    BodyPart.HEAL -> 250
    BodyPart.CLAIM -> 600
    BodyPart.TOUGH -> 10
}

/** Converts abstract BodyPart to BodyPartConstant */
fun asBodyConstant(bodyPartType: BodyPart): BodyPartConstant {
    return when (bodyPartType) {
        BodyPart.MOVE -> MOVE
        BodyPart.WORK -> WORK
        BodyPart.CARRY -> CARRY
        BodyPart.ATTACK -> ATTACK
        BodyPart.RANGED_ATTACK -> RANGED_ATTACK
        BodyPart.HEAL -> HEAL
        BodyPart.CLAIM -> CLAIM
        BodyPart.TOUGH -> TOUGH
    }
}

/** Converts a list of BodyParts to a list of BodyPartConstants */
fun asBodyConstants(body: Array<BodyPart>): Array<BodyPartConstant> {
    return body.map { asBodyConstant(it) }.toTypedArray()
}



fun getSpawnList(): List<StructureSpawn> {
    // provided information, no cost
    return Game.spawns.values.toList()
}

fun getEnergyCapacityAvailable(room: Room): Int = room.energyCapacityAvailable

fun getEnergyAvailable(room: Room): Int = room.energyAvailable


/** A spawn command has all the information needed to spawn a creep */
class SpawnCommand(val body: Array<BodyPart>, val name: String, val spawnOptions: SpawnOptions) {

    fun getEnergyCost(): Int {
        return body.sumOf { bodyPartCost(it) }
    }

    fun countParts(bodyPartType: BodyPart): Int {
        return body.count { it == bodyPartType }
    }

}

/** Generates a new name for a creep */
fun makeName(role: String, id: Int): String {
    return role + id
}

/** For every spawn, assign a spawn command */
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