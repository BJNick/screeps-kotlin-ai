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
    TOWERS,     // 4
    STORAGE,    // 5
}

// Desired counts
fun desiredCountOf(category: DistributionCategory): Int = when (category) {
    UNASSIGNED -> 0
    SPAWN -> 1
    CONTROLLER -> 2
    BUILDERS -> 1
    TOWERS -> 1
    STORAGE -> 0 // UNUSED FOR NOW
    else -> 0
}

// Actual counts
fun actualCountOf(category: DistributionCategory, room: Room): Int {
    return room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.CARRIER &&
            it.memory.distributionCategory == catToInt(category) } }).size
}



fun catToInt(category: DistributionCategory): Int = category.ordinal
fun intToCat(i: Int): DistributionCategory {
    return if (i < catCount())
        DistributionCategory.values()[i]
    else {
        console.log("ERROR: intToCat($i)");
        UNASSIGNED
    }
}
fun catCount(): Int = DistributionCategory.values().size



fun pickDistributionCategory(creep: Creep, room: Room) {
    initializeRoomDistribution(room)
    unassignDistribution(creep.name, room)
    for (i in 1 until catCount()) {
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
    return room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.CARRIER } }).size >= 4
}

fun Creep.findTargetByCategory(seed: Int = 0): HasPosition? {
    var category = this.memory.distributionCategory
    //if (category == 0) pickDistributionCategory(this, room)
    //category = this.memory.distributionCategory
    val constructionSitesPresent = room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty()

    if (category == 0) category = name.hashCode() % (catCount()-1) + 1
    val duty = when (intToCat(category)) {

        SPAWN -> // Supply spawn and extensions
            getVacantSpawnOrExt(room)

        CONTROLLER -> // Supply upgrader creeps
        { // Either container within 3 squares of controller, or upgrader creep ?:
            room.bySort(STRUCTURE_CONTAINER, f = hasNoEnergy and hasTag(CONTROLLER_BUFFER), sort = byDistance(this.pos)) ?:
            room.bySort(
                FIND_MY_CREEPS, f = hasRole(Role.UPGRADER) and hasNoEnergy,
                sort = byMostFree then byDistance(this.pos)
            )
        }

        BUILDERS -> // Supply builder creeps
            room.bySort(FIND_MY_CREEPS, f = hasRole(Role.BUILDER) and { hasNoEnergy(it) || constructionSitesPresent },
                sort = byDistance(this.pos) then byMostFree)

        TOWERS -> // Supply towers
            room.bySort(STRUCTURE_TOWER, f = hasNoEnergy and hasAtMostEnergy(900), sort = byMostFree then byDistance(this.pos)) ?:
            room.bySort(STRUCTURE_CONTAINER, f = hasNoEnergy and hasTag(TOWER_BUFFER), sort = byDistance(this.pos))

        STORAGE -> // Supply storage IF a container is full
            if (room.storage != null && room.storage!!.store.getFreeCapacity(RESOURCE_ENERGY) > 0) room.storage else null

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



