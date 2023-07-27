package bjnick

import bjnick.DistributionCategory.*
import distributionAssignments
import distributionCategory
import role
import screeps.api.*
import screeps.api.structures.StructureContainer

// A module to manage energy distribution via orders to haulers

// Categories: Controller, Spawn, Builders, Towers

enum class DistributionCategory {
    UNASSIGNED, // 0
    SPAWN,      // 1
    CONTROLLER, // 2
    BUILDERS,   // 3
    TOWERS,     // 4
    STORAGE,    // 5
}

// Desired counts
fun desiredCountOf(category: DistributionCategory): Int = when (category) {
    SPAWN -> 1
    CONTROLLER -> 2
    BUILDERS -> 1
    TOWERS -> 1
    STORAGE -> 1 // UNUSED FOR NOW
    else -> 0
}

// Actual counts
fun actualCountOf(category: DistributionCategory, room: Room): Int {
    return room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.CARRIER &&
            it.memory.distributionCategory == catToInt(category) } }).size
}



fun catToInt(category: DistributionCategory): Int = category.ordinal
fun intToCat(i: Int): DistributionCategory = DistributionCategory.values()[i]
fun catCount(): Int = DistributionCategory.values().size



fun pickDistributionCategory(creep: Creep, room: Room) {
    initializeRoomDistribution(room)
    unassignDistribution(creep.name, room)
    for (i in 1..catCount()) {
        if (desiredCountOf(intToCat(i)) > actualCountOf(intToCat(i), room)) {
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
    room.memory.distributionAssignments[catToInt(category)] = creep.name // THIS MEMORY IS IGNORED
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
    //if (category == 0) pickDistributionCategory(this, room)
    //category = this.memory.distributionCategory
    if (category == 0) category = name.hashCode() % (catCount()-1) + 1
    val duty = when (intToCat(category)) {

        SPAWN -> // Supply spawn and extensions
            getVacantSpawnOrExt(room)

        CONTROLLER -> // Supply upgrader creeps
        { // Either container within 3 squares of controller, or upgrader creep
            room.byOrder(STRUCTURE_CONTAINER, f = hasFreeCapacity and withinRange(3, room.controller!!.pos))
                ?.unsafeCast<StructureContainer>() ?:
            room.bySort(
                FIND_MY_CREEPS, f = hasRole(Role.UPGRADER) and hasFreeCapacity,
                sort = byMostFree then byDistance(this.pos)
            )
        }

        BUILDERS -> // Supply builder creeps
            room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.BUILDER &&
                    it.store.getFreeCapacity() > 0 } })
                .minByOrNull { it.store.getUsedCapacity() }

        TOWERS -> // Supply towers
            room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER &&
                    it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 100 } })
                .minByOrNull { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }

        STORAGE -> // Supply storage IF a container is full
            room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_STORAGE &&
                    it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 } })
                .minByOrNull { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }

        else -> null
    }
    return duty ?: findEnergyTarget(room)
}

fun Creep.pickupLocationBias(): RoomPosition {
    var category = this.memory.distributionCategory
    //if (category == 0) pickDistributionCategory(this, room)
    //category = this.memory.distributionCategory
    if (category == 0) category = name.hashCode() % (catCount()-1) + 1

    val biasPos = when (intToCat(category)) {
        SPAWN -> room.find(FIND_MY_SPAWNS).firstOrNull()
        CONTROLLER -> room.controller
        BUILDERS -> room.find(FIND_MY_CONSTRUCTION_SITES).firstOrNull()
        TOWERS -> room.find(FIND_MY_STRUCTURES,
            options { filter = { it.structureType == STRUCTURE_TOWER } }).firstOrNull()
        else -> null
    }
    return (biasPos ?: this).pos
}



