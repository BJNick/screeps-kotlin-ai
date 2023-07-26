package bjnick

import assignedRoom
import assignedSource
import collecting
import collectingPriority
import distributesEnergy
import homeRoom
import lastRoom
import role
import screeps.api.*
import screeps.api.structures.*
import screeps.utils.unsafe.jsObject
import settlementRoom
import targetID
import kotlin.math.max
import kotlin.math.min
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

fun Creep.cornerBias(): RoomPosition {
    return when (name.hashCode() % 4) {
        0 -> RoomPosition(0, 0, room.name)
        1 -> RoomPosition(49, 0, room.name)
        2 -> RoomPosition(0, 49, room.name)
        else -> RoomPosition(49, 49, room.name)
    }
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
fun Creep.collectFromHarvesters(fromRoom: Room = this.room, bias: RoomPosition = pos): Boolean {

    val harvesterCreep = fromRoom.find(FIND_MY_CREEPS, options { filter = { it.store[RESOURCE_ENERGY] > 0 &&
            (it.memory.role == Role.HARVESTER || it.memory.role == Role.OUTER_HARVESTER) }})
        .sortedBy { it.store[RESOURCE_ENERGY]?.times(-1) }
        .minByOrNull { it.pos.getRangeTo(this.pos)/5 }

    val harvesterContainer = fromRoom.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER }})
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] > this.store.getFreeCapacity() }
        .minByOrNull { it.pos.getRangeTo(this.pos)/5 }

    if (harvesterContainer != null && (harvesterCreep == null || harvesterCreep.store.getFreeCapacity() > 0)) {
        moveIfNotInRange(harvesterContainer, this.withdraw(harvesterContainer, RESOURCE_ENERGY), "collectEnergy")
        return true
    }

    if (harvesterCreep != null) {
        moveIfNotInRange(harvesterCreep, harvesterCreep.transfer(this, RESOURCE_ENERGY), "collectEnergy")
        return true
    }
    return false
}

/**
 * Collect from the closest cheapest destination
 */
fun Creep.collectFromClosest(fromRoom: Room = this.room) {

    val takeFromCarriers = getVacantSpawnOrExt(room) == null

    val harvesterCreep = fromRoom.find(FIND_MY_CREEPS, options { filter = { it.store[RESOURCE_ENERGY] >= 20 &&
            (it.memory.role == Role.HARVESTER || it.memory.role == Role.OUTER_HARVESTER ||
                (it.memory.role == Role.CARRIER && takeFromCarriers)) }})
        .sortedBy { it.store[RESOURCE_ENERGY]?.times(-1) }
        .minByOrNull { it.pos.getRangeTo(this.pos)/5 }

    val harvesterContainer = fromRoom.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER }})
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] > this.store.getFreeCapacity() }
        .minByOrNull { it.pos.getRangeTo(this.pos)/5 }


    if (harvesterContainer != null && (harvesterCreep == null || harvesterContainer.pos.getRangeTo(this.pos) <= harvesterCreep.pos.getRangeTo(this.pos))) {
        moveIfNotInRange(harvesterContainer, this.withdraw(harvesterContainer, RESOURCE_ENERGY), "collectEnergy")
        return
    }

    if (harvesterCreep != null)
        moveIfNotInRange(harvesterCreep, harvesterCreep.transfer(this, RESOURCE_ENERGY), "collectEnergy")
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

        // OTHER STRUCTURES SHOULD USE TRANSFER
        is StructureRoad -> repair(target)
        is StructureWall -> repair(target)
        is StructureRampart -> repair(target)

        else -> if (target as? StoreOwner != null)  transfer(target, RESOURCE_ENERGY) else
            return logError("putEnergy: target $target is of unsupported structure type")
    }

    moveIfNotInRange(target, err, "putEnergy")
}

fun Creep.goRepair(target: Structure?): Boolean {
    if (target == null) return false
    return moveIfNotInRange(target, repair(target), "goRepair")
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

fun Creep.moveIfNotInRange(target: HasPosition, err: ScreepsReturnCode, function: String = "moveIfNotInRange"): Boolean {
    if (err == ERR_NOT_IN_RANGE || err == ERR_NOT_ENOUGH_RESOURCES) {

        moveWithin(target.pos, 0)

        return false
    } else if (err != OK) {
        logError("$function: error $err for " + this.name)
        return false
    }
    return true
}

fun Creep.moveWithin(target: RoomPosition, dist: Int = 0): ScreepsReturnCode {
    if (pos.getRangeTo(target) > dist) {
        return moveTo(target, options { visualizePathStyle = jsObject<RoomVisual.ShapeStyle>
                { this.stroke = pathColor(); this.lineStyle = LINE_STYLE_DASHED };
            costCallback = ::costCallbackAvoidBorder; ignoreCreeps = nearBorder() || !hasCreepsNearby(1)  })
    }
    return OK
}

fun costCallbackAvoidBorder(roomName: String, costMatrix: PathFinder.CostMatrix): PathFinder.CostMatrix {
    val newCost = 6 // 6 is more than swamp 5
    for (x in 0..49) {
        costMatrix[x, 0] = newCost
        costMatrix[x, 49] = newCost
    }
    for (y in 0..49) {
        costMatrix[0, y] = newCost
        costMatrix[49, y] = newCost
    }
    return costMatrix
}

fun Creep.nearBorder(): Boolean {
    return pos.x <= 1 || pos.x >= 48 || pos.y <= 1 || pos.y >= 48
}

fun Creep.atBorder(): Boolean {
    return pos.x <= 0 || pos.x >= 49 || pos.y <= 0 || pos.y >= 49
}

fun Creep.simpleMove(target: RoomPosition) {
    // Draw a dashed line one tile long towards the target
    room.visual.poly(arrayOf(pos, target), options { stroke = pathColor(); lineStyle = LINE_STYLE_DASHED; opacity = 0.2 })

    if (pos.roomName != target.roomName) return
    if (target.x < pos.x) {
        if (target.y < pos.y) {
            move(TOP_LEFT)
        } else if (target.y == pos.y) {
            move(LEFT)
        } else if (target.y > pos.y) {
            move(BOTTOM_LEFT)
        }
    } else if (target.x == pos.x) {
        if (target.y < pos.y) {
            move(TOP)
        } else if (target.y > pos.y) {
            move(BOTTOM)
        }
    } else if (target.x > pos.x) {
        if (target.y < pos.y) {
            move(TOP_RIGHT)
        } else if (target.y == pos.y) {
            move(RIGHT)
        } else if (target.y > pos.y) {
            move(BOTTOM_RIGHT)
        }
    }
}

fun Creep.hasCreepsNearby(radius: Int): Boolean {
    return room.lookAtAreaAsArray(pos.y - radius, pos.x - radius, pos.y + radius, pos.x + radius)
        .any { it.type == LOOK_CREEPS && it.creep != null && it.creep!!.my && it.creep!!.id != id }
}

val STORE_STRUCTURES = arrayOf(STRUCTURE_SPAWN, STRUCTURE_EXTENSION, STRUCTURE_TOWER)

// TODO: Save this for use in all creeps
fun Creep.getVacantSpawnOrExt(room: Room): StoreOwner? {
    return room.bySort(arrayOf(STRUCTURE_SPAWN, STRUCTURE_EXTENSION), f = hasFreeCapacity, sort = byDistance(pos))
}

fun Creep.getVacantTower(room: Room, maxEnergy: Int): StoreOwner? {
    return room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER } })
        .map { it.unsafeCast<StoreOwner>() }
        .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) && it.store[RESOURCE_ENERGY] < maxEnergy }
        .minByOrNull { it.pos.getRangeTo(myCollectingPriority()) } // CLOSEST ???
}

fun Creep.getVacantStoreStructure(room: Room): StoreOwner? {
    return room.find(FIND_STRUCTURES, options { filter = { it.structureType in STORE_STRUCTURES } })
        .map { it.unsafeCast<StoreOwner>() }
        .firstOrNull { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun Creep.getVacantDistributorCreep(room: Room): StoreOwner? {
    return room.find(FIND_MY_CREEPS,
        options {
            filter = { it.memory.distributesEnergy && it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY)}
        })
        .sortedBy { it.store[RESOURCE_ENERGY] }.minByOrNull { it.pos.getRangeTo(myCollectingPriority()) } // CLOSEST ???
}

// TODO: Prioritize construction sites by type
fun Creep.getConstructionSite(room: Room): ConstructionSite? {
    val containerSite = room.find(FIND_CONSTRUCTION_SITES, options { filter = { it.structureType == STRUCTURE_CONTAINER } }).firstOrNull()
    if (containerSite != null) return containerSite
    return room.find(FIND_CONSTRUCTION_SITES).sortedBy { it.pos.getRangeTo(pos) }.minByOrNull { -it.progress }
}

// Structures that need to be repaired, lowest hits first
fun Creep.findRepairTarget(hitsLowerThan: Int): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(myRepairRandomSeed()))
}

fun StructureTower.findTowerRepairTarget(hitsLowerThan: Int): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(id.hashCode()))
}

fun StructureTower.findTowerRepairTarget(hitsLowerThan: Int, type: StructureConstant): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.structureType == type && it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(id.hashCode()))
}

fun Creep.findCloseRepairTarget(hitsLowerThan: Int): Structure? {
    return room.lookAtAreaAsArray(pos.y - 1, pos.x - 1, pos.y + 1, pos.x + 1)
        .filter { it.type == LOOK_STRUCTURES && it.structure != null && (it.structure?.hits < it.structure?.hitsMax && it.structure?.hits < hitsLowerThan) }
        .randomOrNull(Random(myRepairRandomSeed()))?.structure
        //options { filter = { it.hits < it.hitsMax && it.hits < hitsLowerThan } }).randomOrNull(Random(myRepairRandomSeed()))
}

// Structures that need to be repaired, lowest hits first
fun Creep.findCriticalRepairTarget(): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < 650 } })
        .sortedBy { it.pos.getRangeTo(myRepairPriority()) }.minByOrNull { it.hits }
}

fun Creep.findWallConstructionSite(): ConstructionSite? {
    return room.find(
        FIND_CONSTRUCTION_SITES,
        options { filter = { it.structureType == STRUCTURE_WALL || it.structureType == STRUCTURE_RAMPART } })
        .minByOrNull { it.pos.getRangeTo(myRepairPriority()) }
}

fun Creep.findContainerRepairTarget(): Structure? {
    return room.find(FIND_STRUCTURES,
        options { filter = { it.structureType == STRUCTURE_CONTAINER && it.hits < it.hitsMax && it.hits < 30000 } })
        .sortedBy { it.pos.getRangeTo(myRepairPriority()) }.minByOrNull { it.hits }
}

fun Creep.findWorkEnergyTarget(room: Room): HasPosition? {
    // TODO This is specific to settler creeps
    return getVacantSpawnOrExt(room) ?: arrayOf(getVacantTower(room, 800), findContainerRepairTarget(),
        findRepairTarget(200),
        getVacantDistributorCreep(room),
        getConstructionSite(room), findRepairTarget(5000), room.controller).filterNotNull().firstOrNull()
}

fun Creep.findEnergyTarget(room: Room): HasPosition? { // TODO - which creeps are using this?
    return getVacantSpawnOrExt(room) ?: arrayOf(getVacantTower(room, 800),
        getVacantDistributorCreep(room)).filterNotNull().randomOrNull(Random(name.hashCode()))
}

fun Creep.findDroppedEnergy(room: Room): HasPosition? {
    val tombs = room.find(FIND_TOMBSTONES, options { filter = { it.store[RESOURCE_ENERGY] > 0 } })
    val dropped = room.find(FIND_DROPPED_RESOURCES, options { filter = { it.resourceType == RESOURCE_ENERGY && it.amount > 0 } })
    return (dropped + tombs).maxByOrNull (byDistance(pos).reversed() then { when (it) {
        is Tombstone -> it.store[RESOURCE_ENERGY] ?: 0
        is Resource -> it.amount
        else -> 0
    }})
}

fun Creep.findBufferContainer(): StructureContainer? {
    return room.find(
        FIND_STRUCTURES,
        options { filter = { it.structureType == STRUCTURE_CONTAINER && it.pos.getRangeTo(pos) < 2 } })
        .map { it.unsafeCast<StructureContainer>() }
        .firstOrNull { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }
}

fun Creep.findClosestContainer(): StructureContainer? {
    return (room.find(FIND_STRUCTURES,
        options { filter = { (it.structureType == STRUCTURE_CONTAINER || it.structureType == STRUCTURE_TOWER) &&
                it.unsafeCast<StructureContainer>().store.getFreeCapacity(RESOURCE_ENERGY) > 0 } })
            )
        .map { it.unsafeCast<StructureContainer>() }
        .sortedBy { it.structureType != STRUCTURE_TOWER }
        .minByOrNull { it.pos.getRangeTo(pos)/3 }
}

fun Creep.findFullestContainer(): StructureContainer? {
    return (room.find(FIND_STRUCTURES,
        options { filter = { (it.structureType == STRUCTURE_CONTAINER) &&
                it.unsafeCast<StructureContainer>().store.getUsedCapacity(RESOURCE_ENERGY) > 0 } })
            )
        .map { it.unsafeCast<StructureContainer>() }
        .maxByOrNull { it.store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }
}

fun Creep.lockTarget(target: Identifiable?) {
    memory.targetID = target?.id ?: ""
}

fun Creep.getTarget(): Identifiable? {
    if (memory.targetID == "") return null
    return Game.getObjectById(memory.targetID)
}

fun Creep.clearTarget() {
    memory.targetID = ""
}

fun needsRepair(target: Identifiable?): Boolean {
    if (target == null) return false
    return target is Structure && target.hits < target.hitsMax && target.hits < 10000
}

fun Creep.stepAwayFromBorder(): Boolean {
    if (pos.x <= 0) {
        move(RIGHT); return true
    } else if (pos.x >= 49) {
        move(LEFT); return true
    } else if (pos.y <= 0) {
        move(BOTTOM); return true
    } else if (pos.y >= 49) {
        move(TOP); return true
    }
    return false
}

fun Creep.moveToRoom(roomName: String): Boolean {
    if (room.name != roomName) {
        moveTo(RoomPosition(25, 25, roomName), options { visualizePathStyle = jsObject<RoomVisual.ShapeStyle>
        { this.stroke = pathColor(); this.lineStyle = LINE_STYLE_SOLID } })
        return true
    }
    return false
}

fun Creep.gotoAssignedRoom(): Boolean {
    if (memory.assignedRoom != "") {
        return moveToRoom(memory.assignedRoom)
    }
    return false
}

fun Creep.gotoHomeRoom(): Boolean {
    if (memory.homeRoom != "") {
        return moveToRoom(memory.homeRoom)
    }
    return false
}

fun Creep.loadAssignedRoomAndHome(): Boolean {
    memory.homeRoom = Memory.homeRoom
    memory.assignedRoom = Memory.settlementRoom
    if (memory.homeRoom == "" || memory.assignedRoom == "") {
        console.log("$name: No home/assigned room")
        return false
    }
    return true
}

fun Room.getTotalContainerEnergy(): Int {
    return find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER || it.structureType == STRUCTURE_STORAGE } })
        .map { it.unsafeCast<StructureContainer>().store[RESOURCE_ENERGY] ?: 0 }.sum()
}

fun Creep.recordBorderMovements() {
    if (atBorder()) {
        if (memory.lastRoom == memory.assignedRoom && room.name == memory.homeRoom ||
            room.name == memory.assignedRoom && memory.lastRoom == memory.homeRoom)
            recordImportExport(memory.lastRoom, room.name, store.getUsedCapacity())
    }
    if (nearBorder()) {
        memory.lastRoom = room.name
    }
}

fun bindCoordinate(v: Int): Int {
    return max(0, min(49, v))
}
