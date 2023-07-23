package bjnick

import collecting
import distributesEnergy
import role
import screeps.api.*
import screeps.api.structures.*

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


fun Creep.collectFromASource(fromRoom: Room = this.room) {
    val sources = fromRoom.find(FIND_SOURCES)
    if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
        moveTo(sources[0].pos)
    }
}


/**
 * Collect from the most convenient source of energy
 */
fun Creep.collectFromHarvesters(fromRoom: Room = this.room) {

    val harvesterCreeps = fromRoom.find(FIND_MY_CREEPS)
        .filter { it.memory.role == Role.HARVESTER || it.memory.role == Role.SETTLER }
        .filter { it.store[RESOURCE_ENERGY] > 0 }
        .sortedBy { it.store[RESOURCE_ENERGY] }

    harvesterCreeps.lastOrNull()?.let {
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

        is StructureController -> upgradeController(target)
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
        logError("$function: error $err")
    }
}

val STORE_STRUCTURES = arrayOf(STRUCTURE_SPAWN, STRUCTURE_EXTENSION, STRUCTURE_TOWER, STRUCTURE_STORAGE, STRUCTURE_CONTAINER)

// TODO: Save this for use in all creeps
fun getVacantSpawnAndExt(room: Room): List<StoreOwner?> {
    return room.find(FIND_STRUCTURES)
        .filter { it.structureType == STRUCTURE_SPAWN || it.structureType == STRUCTURE_EXTENSION }
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun getVacantStoreStructures(room: Room): List<StoreOwner?> {
    return room.find(FIND_STRUCTURES)
        .filter { it.structureType in STORE_STRUCTURES }
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun getVacantDistributorCreeps(room: Room): List<StoreOwner?> {
    return room.find(FIND_MY_CREEPS)
        .filter { it.memory.distributesEnergy }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
        .sortedBy { it.store[RESOURCE_ENERGY] }
}

// TODO: Prioritize construction sites by type
fun getConstructionSites(room: Room): List<ConstructionSite> {
    return room.find(FIND_CONSTRUCTION_SITES).sortedBy { -it.progress }
}

fun findEnergyTarget(room: Room): HasPosition? {
    val targets = getVacantSpawnAndExt(room) + getVacantDistributorCreeps(room) + getVacantStoreStructures(room) + getConstructionSites(room) + room.controller
    return targets.firstOrNull()
}

fun findDroppedEnergy(room: Room): HasPosition? {
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