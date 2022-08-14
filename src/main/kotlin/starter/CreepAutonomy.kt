package starter

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

/**
 * Collect from the most convenient source of energy
 */
fun Creep.collectEnergy(fromRoom: Room = this.room) {

    val sources = fromRoom.find(FIND_SOURCES)
    if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
        moveTo(sources[0].pos)
    }

}

// TODO: Consider overloads for different types of structures
/**
 * Puts energy into the specified structure
 */
fun Creep.putEnergy(target: HasPosition?) {

    if (target == null)
        return

    if (target !is Structure && target !is ConstructionSite)
        return logError("putEnergy: target $target is not a Structure or ConstructionSite")

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val err = when (target) {

        is StructureController -> upgradeController(target)
        is ConstructionSite -> build(target)

        else -> if (target as? StoreOwner != null)  transfer(target, RESOURCE_ENERGY) else
            return logError("putEnergy: target $target is of unsupported structure type")
    }

    if (err == ERR_NOT_IN_RANGE) {
        when (target) {
            is Structure -> moveTo(target.pos)
            is ConstructionSite -> moveTo(target.pos)
        }
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

// TODO: Prioritize construction sites by type
fun getConstructionSites(room: Room): Array<ConstructionSite> {
    return room.find(FIND_CONSTRUCTION_SITES)
}

fun findEnergyTarget(room : Room) : HasPosition? {
    val targets = getVacantSpawnAndExt(room) + getVacantStoreStructures(room) + getConstructionSites(room) + room.controller
    return targets.firstOrNull()
}
