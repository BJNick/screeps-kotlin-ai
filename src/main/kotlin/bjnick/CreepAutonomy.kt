package bjnick

import assignedSource
import collecting
import collectingPriority
import distributesEnergy
import role
import screeps.api.*
import screeps.api.structures.*
import screeps.utils.unsafe.jsObject
import kotlin.random.Random

/*object pathStyle: RoomVisual.ShapeStyle {
    override var fill: String? = "transparent";
    override var stroke: String? = "#fff";
    override var lineStyle: LineStyleConstant? = LINE_STYLE_DASHED;
    override var strokeWidth: Double? = .15;
    override var opacity: Double? = .1;
}*/

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

fun Creep.assignedSource(pick: Boolean = true): Source? {
    if (memory.assignedSource != "") {
        return Game.getObjectById(memory.assignedSource)
    }
    if (pick) {
        val source = room.pickSourceForHarvester(this)
        room.assignSource(this, source)
        return source
    } else {
        return null
    }
}

fun Creep.myCollectingPriority(): RoomPosition {
    val controller = room.controller!!.pos
    val spawn = room.find(FIND_MY_SPAWNS)[0].pos
    val tower = room.find(FIND_MY_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER } })
        .firstOrNull()?.pos ?: RoomPosition(0, 0, room.name)
    val priority = when (name.hashCode() % 3) {
        0 -> controller
        1 -> spawn
        else -> tower
    }
    this.memory.collectingPriority = "${priority.x},${priority.y}"
    return priority
}

fun Creep.myRepairPriority(): RoomPosition {
    val edge1 = RoomPosition(0, 0, room.name)
    val edge2 = RoomPosition(49, 49, room.name)
    val priority = when (name.hashCode() % 2) {
        0 -> edge1
        else -> edge2
    }
    return priority
}

fun Creep.myRepairRandomSeed(): Int {
    return name.hashCode()
}

fun Creep.collectFromASource(fromRoom: Room = this.room) {
    //val source = fromRoom.find(FIND_SOURCES)[0]
    val source = assignedSource() ?: throw RuntimeException("No source assigned")
    moveIfNotInRange(source, harvest(source), "collectFromASource")
}


/**
 * Collect from the most full harvester
 */
fun Creep.collectFromHarvesters(fromRoom: Room = this.room) {

    val harvesterCreeps = fromRoom.find(FIND_MY_CREEPS)
        .filter { it.memory.role == Role.HARVESTER || it.memory.role == Role.SETTLER }
        .filter { it.store[RESOURCE_ENERGY] > 0 }
        .sortedBy { it.store[RESOURCE_ENERGY]?.times(-1) }
        .sortedBy { it.pos.getRangeTo(myCollectingPriority())/5 }

    val harvesterContainers = fromRoom.find(FIND_STRUCTURES)
        .filter { it.structureType == STRUCTURE_CONTAINER }
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] > this.store.getFreeCapacity() }
        .sortedBy { it.pos.getRangeTo(myCollectingPriority())/5 }

    if (harvesterContainers.isNotEmpty()) {
        val container = harvesterContainers[0]
        moveIfNotInRange(container, this.withdraw(container, RESOURCE_ENERGY), "collectEnergy")
        return
    }

    harvesterCreeps.firstOrNull()?.let {
        moveIfNotInRange(it, it.transfer(this, RESOURCE_ENERGY), "collectEnergy")
    }

}

/**
 * Collect from the closest cheapest destination
 */
fun Creep.collectFromClosest(fromRoom: Room = this.room) {

    val takeFromCarriers = getVacantSpawnAndExt(room).isEmpty()

    val harvesterCreeps = fromRoom.find(FIND_MY_CREEPS)
        .filter { it.memory.role == Role.HARVESTER || it.memory.role == Role.SETTLER ||
                (it.memory.role == Role.CARRIER && takeFromCarriers) }
        .filter { it.store[RESOURCE_ENERGY] > 0 }
        .sortedBy { it.store[RESOURCE_ENERGY]?.times(-1) }
        .sortedBy { it.pos.getRangeTo(this.pos)/5 }

    val harvesterContainers = fromRoom.find(FIND_STRUCTURES)
        .filter { it.structureType == STRUCTURE_CONTAINER }
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] > this.store.getFreeCapacity() }
        .sortedBy { it.pos.getRangeTo(this.pos)/5 }


    if (harvesterContainers.isNotEmpty() && (harvesterCreeps.isEmpty() || harvesterContainers[0].pos.getRangeTo(this.pos) <= harvesterCreeps[0].pos.getRangeTo(this.pos))) {
        val container = harvesterContainers[0]
        moveIfNotInRange(container, this.withdraw(container, RESOURCE_ENERGY), "collectEnergy")
        return
    }

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

fun Creep.goRepair(target: Structure?) {
    if (target == null) return
    moveIfNotInRange(target, repair(target), "goRepair")
}

fun Creep.stackToCarriers(): Boolean {

    if (store[RESOURCE_ENERGY] == 0) return false

    val nearbyCarriers = room.find(FIND_MY_CREEPS, options { filter = {
                it.memory.role == Role.CARRIER && it.pos.isNearTo(pos)
                && it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY)
                && it.store[RESOURCE_ENERGY] > store[RESOURCE_ENERGY] }})
        .sortedBy { it.store[RESOURCE_ENERGY] }

    nearbyCarriers.lastOrNull()?.let {
        moveIfNotInRange(it, transfer(it, RESOURCE_ENERGY), "collectEnergy")
        say("stack!")
        return true
    }

    return false
}

fun Creep.moveIfNotInRange(target: HasPosition, err: ScreepsReturnCode, function: String = "moveIfNotInRange") {
    if (err == ERR_NOT_IN_RANGE || err == ERR_NOT_ENOUGH_RESOURCES) {
        moveTo(target.pos, options { visualizePathStyle = jsObject<RoomVisual.ShapeStyle> { this.stroke = pathColor(); this.lineStyle = LINE_STYLE_SOLID } })
    } else if (err != OK) {
        logError("$function: error $err for " + this.name)
    }
}

val STORE_STRUCTURES = arrayOf(STRUCTURE_SPAWN, STRUCTURE_EXTENSION, STRUCTURE_TOWER)

// TODO: Save this for use in all creeps
fun Creep.getVacantSpawnAndExt(room: Room): List<StoreOwner?> {
    return room.find(FIND_STRUCTURES, options { filter =
        { it.structureType in arrayOf(STRUCTURE_SPAWN, STRUCTURE_EXTENSION, STRUCTURE_TOWER) } })
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
        .sortedBy { it.pos.getRangeTo(myCollectingPriority()) } // CLOSEST ???
}

fun Creep.getVacantStoreStructures(room: Room): List<StoreOwner?> {
    return room.find(FIND_STRUCTURES, options { filter = { it.structureType in STORE_STRUCTURES } })
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun Creep.getVacantDistributorCreeps(room: Room): List<StoreOwner?> {
    return room.find(FIND_MY_CREEPS, options { filter = { it.memory.distributesEnergy && it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) } })
        .sortedBy { it.store[RESOURCE_ENERGY] }
        .sortedBy { it.pos.getRangeTo(myCollectingPriority()) } // CLOSEST ???
}

// TODO: Prioritize construction sites by type
fun Creep.getConstructionSites(room: Room): List<ConstructionSite> {
    return room.find(FIND_CONSTRUCTION_SITES).sortedBy { it.pos.getRangeTo(pos) }.sortedBy { -it.progress }
}

// Structures that need to be repaired, lowest hits first
fun Creep.findRepairTarget(hitsLowerThan: Int): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(myRepairRandomSeed()))
}

fun StructureTower.findTowerRepairTarget(hitsLowerThan: Int): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(id.hashCode()))
}

fun Creep.findCloseRepairTarget(hitsLowerThan: Int): Structure? {
    return room.lookAtAreaAsArray(pos.y - 1, pos.x - 1, pos.y + 1, pos.x + 1)
        .filter { it.type == LOOK_STRUCTURES && it.structure != null && (it.structure?.hits < it.structure?.hitsMax && it.structure?.hits < hitsLowerThan) }
        .randomOrNull(Random(myRepairRandomSeed()))?.structure
        //options { filter = { it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(myRepairRandomSeed()))
}

// Structures that need to be repaired, lowest hits first
fun Creep.findCriticalRepairTargets(): List<Structure> {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < 650 } }).sortedBy { it.pos.getRangeTo(myRepairPriority()) }.sortedBy { it.hits }
}

fun Creep.findWallConstructionSites(): List<ConstructionSite> {
    return room.find(FIND_CONSTRUCTION_SITES, options { filter = { it.structureType == STRUCTURE_WALL || it.structureType == STRUCTURE_RAMPART } }).sortedBy { it.pos.getRangeTo(myRepairPriority()) }
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

fun Creep.findBufferContainer(): StructureContainer? {
    return room.find(FIND_STRUCTURES).filter { it.structureType == STRUCTURE_CONTAINER }
        .filter { it.pos.getRangeTo(pos) < 2 }
        .map { it.unsafeCast<StructureContainer>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
        .firstOrNull()
}

fun Creep.findClosestContainer(): StructureContainer? {
    return room.find(FIND_STRUCTURES).filter { it.structureType == STRUCTURE_CONTAINER || it.structureType == STRUCTURE_TOWER }
        .map { it.unsafeCast<StructureContainer>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
        .sortedBy { it.pos.getRangeTo(pos) }
        .firstOrNull()
}
