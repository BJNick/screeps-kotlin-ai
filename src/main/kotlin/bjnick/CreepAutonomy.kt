package bjnick

import assignedSource
import collecting
import distributesEnergy
import role
import screeps.api.*
import screeps.api.RoomVisual.LineStyle
import screeps.api.structures.*

/*object pathStyle: RoomVisual.ShapeStyle {
    override var fill: String? = "transparent";
    override var stroke: String? = "#fff";
    override var lineStyle: LineStyleConstant? = LINE_STYLE_DASHED;
    override var strokeWidth: Double? = .15;
    override var opacity: Double? = .1;
}*/

fun distributorPriority(role: String): Int = when (role) {
    Role.BUILDER -> 1
    Role.UPGRADER -> 10
    else -> 99
}

fun logError(message: Any?) : Unit = console.log(message)

fun Creep.isCollecting(): Boolean {
    if (!memory.collecting && store[RESOURCE_ENERGY] == 0) {
        memory.collecting = true
        say("⛏ collect")
    }
    if (memory.collecting && store[RESOURCE_ENERGY] == store.getCapacity()) {
        memory.collecting = false
        say("⚡ put away")
    }
    return memory.collecting
}

fun Creep.assignedSource(): Source {
    if (memory.assignedSource != "") {
        return Game.getObjectById(memory.assignedSource) ?: throw RuntimeException("No source found")
    }
    val source = room.pickSourceForHarvester(this)
    room.assignSource(this, source)
    return source
}

fun Creep.collectFromASource(fromRoom: Room = this.room) {
    //val source = fromRoom.find(FIND_SOURCES)[0]
    val source = assignedSource()
    if (harvest(source) == ERR_NOT_IN_RANGE) {
        moveTo(source.pos)
    }
}


/**
 * Collect from the most full harvester
 */
fun Creep.collectFromHarvesters(fromRoom: Room = this.room) {

    val harvesterCreeps = fromRoom.find(FIND_MY_CREEPS)
        .filter { it.memory.role == Role.HARVESTER || it.memory.role == Role.SETTLER }
        .filter { it.store[RESOURCE_ENERGY] > 0 }
        .sortedBy { it.pos.getRangeTo(this.pos).times(-1)/5 }
        .sortedBy { it.store[RESOURCE_ENERGY] }

    harvesterCreeps.lastOrNull()?.let {
        moveIfNotInRange(it, it.transfer(this, RESOURCE_ENERGY), "collectEnergy")
    }

}

/**
 * Collect from the closest cheapest destination
 */
fun Creep.collectFromClosest(fromRoom: Room = this.room) {

    val harvesterCreeps = fromRoom.find(FIND_MY_CREEPS)
        .filter { it.memory.role == Role.HARVESTER || it.memory.role == Role.SETTLER || it.memory.role == Role.CARRIER }
        .filter { it.store[RESOURCE_ENERGY] > 0 }
        .sortedBy { it.store[RESOURCE_ENERGY]?.times(-1) }
        .sortedBy { it.pos.getRangeTo(this.pos)/5 }

    harvesterCreeps.firstOrNull()?.let {
        moveIfNotInRange(it, it.transfer(this, RESOURCE_ENERGY), "collectEnergy")
    }

}


fun Creep.collectFrom(target: HasPosition?) {
    if (target == null) return

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val err = when (target) {

        is Source -> harvest(target)
        is Tombstone -> withdraw(target, RESOURCE_ENERGY)
        is Creep -> target.transfer(this, RESOURCE_ENERGY)
        is Resource -> pickup(target)

        else -> if (target as? StoreOwner != null)  withdraw(target, RESOURCE_ENERGY) else
            return logError("collectFrom: target $target is of unsupported type")
    }

    moveIfNotInRange(target, err, "collectFrom")
}

// TODO: Consider overloads for different types of structures
/**
 * Puts energy into the specified structure
 */
fun Creep.putEnergy(target: HasPosition?) {

    if (target == null)
        return

    if (target !is Structure && target !is ConstructionSite && target !is Creep)
        return logError("putEnergy: target $target is not a Structure, Creep or ConstructionSite")

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val err = when (target) {

        is StructureController -> {
            // Move as close as possible TODO doesnt work
            val e = upgradeController(target)
            if (e == OK && target.pos.getRangeTo(this.pos) > 1) ERR_NOT_IN_RANGE
            e
        }
        is ConstructionSite -> build(target)
        is Creep -> transfer(target, RESOURCE_ENERGY)

        else -> if (target as? StoreOwner != null)  transfer(target, RESOURCE_ENERGY) else
            return logError("putEnergy: target $target is of unsupported structure type")
    }

    moveIfNotInRange(target, err, "putEnergy")
}

fun Creep.stackToCarriers(): Boolean {

    if (store[RESOURCE_ENERGY] == 0) return false

    val nearbyCarriers = room.find(FIND_MY_CREEPS)
        .filter { it.memory.role == Role.CARRIER }
        .filter { it.pos.isNearTo(pos) }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
        .filter { it.store[RESOURCE_ENERGY] > store[RESOURCE_ENERGY] }
        .sortedBy { it.store[RESOURCE_ENERGY] }

    nearbyCarriers.lastOrNull()?.let {
        moveIfNotInRange(it, transfer(it, RESOURCE_ENERGY), "collectEnergy")
        say("stack!")
        return true
    }

    return false
}

fun Creep.moveIfNotInRange(target: HasPosition, err: ScreepsReturnCode, function: String = "moveIfNotInRange") {
    if (err == ERR_NOT_IN_RANGE) {
        moveTo(target.pos)
    } else if (err != OK) {
        logError("$function: error $err for " + this.name)
    }
}

val STORE_STRUCTURES = arrayOf(STRUCTURE_SPAWN, STRUCTURE_EXTENSION, STRUCTURE_TOWER, STRUCTURE_STORAGE, STRUCTURE_CONTAINER)

// TODO: Save this for use in all creeps
fun Creep.getVacantSpawnAndExt(room: Room): List<StoreOwner?> {
    return room.find(FIND_STRUCTURES)
        .filter { it.structureType == STRUCTURE_SPAWN || it.structureType == STRUCTURE_EXTENSION }
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun Creep.getVacantStoreStructures(room: Room): List<StoreOwner?> {
    return room.find(FIND_STRUCTURES)
        .filter { it.structureType in STORE_STRUCTURES }
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun Creep.getVacantDistributorCreeps(room: Room): List<StoreOwner?> {
    return room.find(FIND_MY_CREEPS)
        .filter { it.memory.distributesEnergy }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
        .sortedBy { it.store[RESOURCE_ENERGY] }
        .sortedBy { distributorPriority(it.memory.role) }
        .sortedBy { it.pos.getRangeTo(pos) } // CLOSEST
}

// TODO: Prioritize construction sites by type
fun Creep.getConstructionSites(room: Room): List<ConstructionSite> {
    return room.find(FIND_CONSTRUCTION_SITES).sortedBy { it.pos.getRangeTo(pos) }.sortedBy { -it.progress }
}

fun Creep.findEnergyTarget(room: Room): HasPosition? {
    val targets = getVacantSpawnAndExt(room) + getVacantDistributorCreeps(room) + getVacantStoreStructures(room) + getConstructionSites(room) + room.controller
    return targets.firstOrNull()
}

fun Creep.findDroppedEnergy(room: Room): HasPosition? {
    val tombs = room.find(FIND_TOMBSTONES)
        .filter { it.store[RESOURCE_ENERGY] > 0 }
    val dropped = room.find(FIND_DROPPED_RESOURCES)
        .filter { it.resourceType == RESOURCE_ENERGY && it.amount > 0 }
    return (dropped + tombs).sortedBy { when (it) {
        is Tombstone -> it.store[RESOURCE_ENERGY]
        is Resource -> it.amount
        else -> 0
    }}.lastOrNull()
}