package bjnick

import bjnick.DistributionCategory.*
import distributionAssignments
import distributionCategory
import role
import screeps.api.*

// A module to manage energy distribution via orders to haulers

// Categories: Controller, Spawn, Builders, Towers

enum class DistributionCategory {
    UNASSIGNED, // 0
    SPAWN,      // 1
    CONTROLLER, // 2
    BUILDERS,   // 3
    TOWERS      // 4
}

fun catToInt(category: DistributionCategory): Int = category.ordinal
fun intToCat(i: Int): DistributionCategory = DistributionCategory.values()[i]
fun catCount(): Int = DistributionCategory.values().size

fun pickDistributionCategory(creep: Creep, room: Room) {
    initializeRoomDistribution(room)
    unassignDistribution(creep.name, room)
    for (i in 1..catCount()) {
        if (room.memory.distributionAssignments[i] == "") {
            assignDistribution(creep, room, intToCat(i))
            return
        }
    }
    // None selected above, select a random one
    val category = creep.name.hashCode() % (catCount()-1) + 1
    assignDistribution(creep, room, intToCat(category))
}

fun initializeRoomDistribution(room: Room) {
    if (room.memory.distributionAssignments.isEmpty())
        room.memory.distributionAssignments = Array(5) { "" }
}

fun assignDistribution(creep: Creep, room: Room, category: DistributionCategory) {
    creep.memory.distributionCategory = catToInt(category)
    initializeRoomDistribution(room)
    room.memory.distributionAssignments[catToInt(category)] = creep.name
}

fun unassignDistribution(creepName: String, room: Room) {
    val category = Memory.creeps.get(creepName)?.distributionCategory ?: return
    Memory.creeps.get(creepName)?.distributionCategory = 0
    if (category == 0) return
    initializeRoomDistribution(room)
    room.memory.distributionAssignments[category] = ""
}

fun useDistributionSystem(room: Room): Boolean {
    // If there are 5 or more carriers, use the distribution system
    return room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.CARRIER } }).size >= 5
}

fun Creep.findTargetByCategory(seed: Int = 0): HasPosition? {
    var category = this.memory.distributionCategory
    if (category == 0) pickDistributionCategory(this, room)
    category = this.memory.distributionCategory
    if (category == 0) category = name.hashCode() % (catCount()-1) + 1
    val duty = when (intToCat(category)) {

        SPAWN -> // Supply spawn and extensions
            getVacantSpawnOrExt(room)

        CONTROLLER -> // Supply upgrader creeps
            room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.UPGRADER &&
                    it.store.getFreeCapacity() > 0 } })
                .minByOrNull { it.store.getUsedCapacity() }

        BUILDERS -> // Supply builder creeps
            room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.BUILDER &&
                    it.store.getFreeCapacity() > 0 } })
                .minByOrNull { it.store.getUsedCapacity() }

        TOWERS -> // Supply towers
            room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER &&
                    it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 } })
                .minByOrNull { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }

        else -> null
    }
    return duty ?: findEnergyTarget(room)
}

fun Creep.pickupLocationBias(): RoomPosition {
    var category = this.memory.distributionCategory
    if (category == 0) pickDistributionCategory(this, room)
    category = this.memory.distributionCategory
    if (category == 0) category = name.hashCode() % (catCount()-1) + 1

    val biasPos = when (intToCat(category)) {
        SPAWN -> room.find(FIND_MY_SPAWNS).firstOrNull()
        CONTROLLER -> room.controller
        BUILDERS -> room.find(FIND_MY_CONSTRUCTION_SITES).firstOrNull()
        TOWERS -> room.find(FIND_MY_STRUCTURES,
            options { filter = { it.structureType == STRUCTURE_TOWER } }).firstOrNull()
        else -> null
    }
    return (biasPos ?: room.find(FIND_MY_SPAWNS).first()).pos
}



