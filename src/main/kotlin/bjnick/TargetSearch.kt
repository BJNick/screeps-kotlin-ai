package bjnick

import assignedRoom
import structureTags
import role
import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureContainer
import kotlin.math.min

// Define filter lambda type
typealias Filter<T> = (T) -> Boolean

/// COMMON FILTERS

// SPECIFIC TO ENERGY
val hasNoEnergy: Filter<Any> = { owner -> owner.unsafeCast<StoreOwner>()
    .store.getFreeCapacity(RESOURCE_ENERGY) > 0 }

val hasSomeEnergy: Filter<Any> = { owner -> owner.unsafeCast<StoreOwner>()
    .store.getUsedCapacity(RESOURCE_ENERGY) > 0 }

fun hasNoneOf(resource: ResourceConstant): Filter<Any> = { owner -> owner.unsafeCast<StoreOwner>()
    .store.getUsedCapacity(resource) == 0 }

fun hasSomeOf(resource: ResourceConstant): Filter<Any> = { owner -> owner.unsafeCast<StoreOwner>()
    .store.getUsedCapacity(resource) > 0 }

fun Structure.isType(vararg types: StructureConstant): Boolean = this.structureType in types
fun isType(vararg types: StructureConstant): Filter<Structure> = { it.structureType in types }
fun isType(types: Array<StructureConstant>): Filter<Structure> = { it.structureType in types }

val lessThanMaxHits: Filter<Attackable> = { structure: Attackable -> structure.hits < structure.hitsMax }
fun <T: Attackable> lessHitsThan(hits: Int): Filter<T> = { structure: T -> structure.hits < hits }

fun hasRole(vararg role: String): Filter<Creep> = { creep -> creep.memory.role in role }

fun hasBodyPart(vararg parts: BodyPartConstant): Filter<Creep> = { creep -> creep.body.any { it.type in parts } }

fun ticksToLiveOver(ticks: Int): Filter<Creep> = { creep -> creep.ticksToLive > ticks }

fun hasAssignedRoom(room: String): Filter<Creep> = { creep -> creep.memory.assignedRoom == room }


fun onOppositeSideOf(pivot: RoomPosition, side: RoomPosition): Filter<HasPosition> = { o ->
    (if (pivot.x < side.x) pivot.x > o.pos.x else pivot.x < o.pos.x && pivot.x != side.x) or
            (if (pivot.y < side.y) pivot.y > o.pos.y else pivot.y < o.pos.y && pivot.y != side.y)
}

fun hasAtLeastEnergy(amount: Int): Filter<Any> =
    { creep -> creep.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) >= amount }

fun hasAtMostEnergy(amount: Int): Filter<Any> =
    { creep -> creep.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) <= amount }

fun hasAtLeast(amount: Int, resource: ResourceConstant): Filter<Any> =
{ creep -> creep.unsafeCast<StoreOwner>().store.getUsedCapacity(resource) >= amount }

fun hasAtMost(amount: Int, resource: ResourceConstant): Filter<Any> =
{ creep -> creep.unsafeCast<StoreOwner>().store.getUsedCapacity(resource) <= amount }

fun withinRange(range: Int, pos: RoomPosition): Filter<HasPosition> = { o -> o.pos.inRangeTo(pos, range) }

val notControllerBuffer: Filter<HasPosition> = {
    it ->
    val room = Game.rooms[it.pos.roomName]!!
    !withinRange(3, it.pos)(room.controller!!) || (room.byOrder(FIND_SOURCES, f=withinRange(1, it.pos)) != null)
}

fun isUnoccupied(except: Creep? = null): Filter<HasPosition> = {
    it -> val creeps = it.pos.lookFor(LOOK_CREEPS)
    creeps!!.isEmpty() || creeps[0] == except
}

fun hasTag(tag: String): Filter<Structure> = { it.hasTag(tag) }
fun hasNoTag(vararg tags: String): Filter<Structure> = { tags.all { tag -> !it.hasTag(tag) } }

// Define an "and" operator for filters
infix fun <T> Filter<T>.and(other: Filter<T>): Filter<T> = { this(it) && other(it) }

// Define an "or" operator for filters
infix fun <T> Filter<T>.or(other: Filter<T>): Filter<T> = { this(it) || other(it) }

// Define a "not"
fun <T> Filter<T>.not(): Filter<T> = { !this(it) }

// Define sorter lambda type
typealias Sorter<T, R> = (T) -> R

// "Then" pair - first compares by the first comparable, then by the second comparable
class ThenPair<A: Comparable<A>,B: Comparable<B>>(val pair: Pair<A, B>) : Comparable<ThenPair<A,B>> {
    override fun compareTo(other: ThenPair<A,B>): Int {
        val result1 = pair.first.compareTo(other.pair.first)
        if (result1 != 0) return result1
        return pair.second.compareTo(other.pair.second)
    }
}

// Define "then" infix operator for sorters - first compares by the first one, if even, compares by the second one
infix fun <T, R: Comparable<R>, B: Comparable<B>> Sorter<T, R>.then(other: Sorter<T, B>): Sorter<T, ThenPair<R,B>> {
    return { obj -> ThenPair(Pair(this(obj), other(obj))) }
}

class ReverseComparable<R: Comparable<R>>(val comparable: R): Comparable<ReverseComparable<R>>  {
    override fun compareTo(other: ReverseComparable<R>): Int {
        return -comparable.compareTo(other.comparable)
    }
}

fun <T,R: Comparable<R>> Sorter<T,R>.reversed(): Sorter<T, ReverseComparable<R>> = { obj: T -> ReverseComparable(this(obj)) }

// COMMON SORTS
fun byDistance(to: RoomPosition, scale: Int = 1): Sorter<HasPosition, Int> = { it.pos.getRangeTo(to)/scale }

val byHits: Sorter<Attackable, Int> = { it.hits }
val byHitsRatio: Sorter<Attackable, Double> = { it.hits.toDouble()/it.hitsMax.toDouble() }
val byLeastMaxHits: Sorter<Attackable, Int> = { it.hitsMax }
val byMaxHits: Sorter<Attackable, dynamic> = byLeastMaxHits.reversed()

val byLeastFree: Sorter<Any, Int> = { it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) ?: 0 }
val byLeastUsed: Sorter<Any, Int> = { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }

val byMostFree = byLeastFree.reversed()
val byMostUsed = byLeastUsed.reversed()

fun byEnoughEnergy(desired: Int): Sorter<Any, Boolean> = { it.unsafeCast<StoreOwner>().store.getUsedCapacity(RESOURCE_ENERGY) < desired  } // reversed
fun byEnoughSpace(desired: Int): Sorter<Any, Boolean> = { it.unsafeCast<StoreOwner>().store.getFreeCapacity(RESOURCE_ENERGY) < desired  } // reversed

fun byOrderIn(array: Array<StructureConstant>): Sorter<Structure, Int> = { array.indexOf(it.structureType) }

val byBorderProximity: Sorter<HasPosition, Int> = {
    min(min(it.pos.x, 49 - it.pos.x), min(it.pos.y, 49 - it.pos.y))
}

fun byLeastBodyParts(vararg parts: BodyPartConstant): Sorter<Creep, Int> = { creep -> creep.body.count { it.type in parts } }
fun byMostBodyParts(vararg parts: BodyPartConstant): Sorter<Creep, dynamic> = byLeastBodyParts(*parts).reversed()

fun <T> byTrueFirst(filter: Filter<T>): Sorter<T, Boolean> = { !filter(it) }

fun byEuclideanDistance(to: RoomPosition, scale: Int = 1): Sorter<HasPosition, Int> = {
    ((it.pos.x - to.x)*(it.pos.x - to.x) + (it.pos.y - to.y)*(it.pos.y - to.y))/scale
}

val byConstructionProgress: Sorter<ConstructionSite, Int> = { -it.progress }

/**
 * Takes in any number of find constants and returns an object that matches the first one that is found
 * If no matching objects are found, returns null
 */
fun <T> Room.byOrder(findConstants: Array<FindConstant<T>>, f: Filter<T> = { _: T -> true } ): dynamic {
    for (findConstant in findConstants) {
        val result = this.find(findConstant, options { filter = f }).firstOrNull()
        if (result != null) return result
    }
    return null
}

fun <T> Room.byOrder(findConstant: FindConstant<T>, f: Filter<T> = { _: T -> true } ): dynamic {
    return byOrder(arrayOf(findConstant), f)
}

fun Room.byOrder(structureConstants: Array<StructureConstant>, f: Filter<Structure> = { _: Structure -> true } ): dynamic {
    //return byOrder(arrayOf(FIND_STRUCTURES), isType(structureConstants) and f)
    for (structureConstant in structureConstants) {
        val result = cachedStructures(this, structureConstant).firstOrNull(f)
        if (result != null) return result
    }
    return null
}

fun Room.byOrder(structureConstant: StructureConstant, f: Filter<Structure> = { _: Structure -> true } ): dynamic {
    return byOrder(arrayOf(structureConstant), f)
}


fun <T> Array<T>.byOrder(f: Filter<T> = { _: T -> true } ): dynamic {
    return this.firstOrNull(f)
}

fun Game.creepCount(f: Filter<Creep> = { _: Creep -> true }): Int = this.creeps.values.count(f)


/**
 * Takes in any number of find constants and returns an object sorted by the specified order
 */
fun <T, R: Comparable<R>> Room.bySort(findConstants: Array<FindConstant<T>>, f: Filter<T> = { _: T -> true }, sort: Sorter<T, R>): dynamic {
    val results = mutableListOf<T>()
    for (findConstant in findConstants) {
        results.addAll(this.find(findConstant, options { filter = f }))
    }
    return results.minByOrNull(sort)
}

fun <T, R: Comparable<R>> Room.bySort(findConstant: FindConstant<T>, f: Filter<T> = { _: T -> true }, sort: Sorter<T, R>): dynamic {
    return bySort(arrayOf(findConstant), f, sort)
}

fun <R: Comparable<R>> Room.bySort(structureConstants: Array<StructureConstant>, f: Filter<Structure> = { _: Structure -> true }, sort: Sorter<Structure, R>): dynamic {
    return bySort(FIND_STRUCTURES, f = isType(structureConstants) and f, sort = {s: Structure -> sort(s)})
    /*val results = mutableListOf<Structure>()
    for (structureConstant in structureConstants) {
        results.addAll(cachedStructures(this, structureConstant).filter(f))
    }
    return results.minByOrNull(sort)*/
}

fun <R: Comparable<R>> Room.bySort(structureConstant: StructureConstant, f: Filter<Structure> = { _: Structure -> true }, sort: Sorter<Structure, R>): dynamic {
    return bySort(arrayOf(structureConstant), f, sort)
}

fun <T, R: Comparable<R>> Array<T>.bySort(f: Filter<T> = { _: T -> true }, sort: Sorter<T, R>): dynamic {
    return this.filter(f).minByOrNull(sort)
}


fun <T, R: Comparable<R>> Room.bySortN(n: Int, findConstants: Array<FindConstant<T>>, f: Filter<T> = { _: T -> true }, sort: Sorter<T, R>): Array<T> {
    val results = mutableListOf<T>()
    for (findConstant in findConstants) {
        results.addAll(this.find(findConstant, options { filter = f }))
    }
    return minN(n, results, sort)
}

fun <R: Comparable<R>> Room.bySortN(n: Int, structureConstants: Array<StructureConstant>, f: Filter<Structure> = { _: Structure -> true }, sort: Sorter<Structure, R>): Array<Structure> {
    return bySortN(n, arrayOf(FIND_STRUCTURES), f = isType(structureConstants) and f, sort = {s: Structure -> sort(s)})
}

fun <T, R: Comparable<R>> minN(n: Int, collection: Iterable<T>, sort: Sorter<T, R>): Array<T> {
    return collection.fold(ArrayList<T>()) { topList, candidate ->
        if (topList.size < n || sort(candidate) < sort(topList.last())) {
            // ideally insert at the right place
            topList.add(candidate)
            topList.sortBy(sort)
            // trim to size
            if (topList.size > n)
                topList.removeAt(n)
        }
        topList
    }.toTypedArray()
}


// TODO: Random element ?


fun Creep.findConvenientEnergy(room: Room = this.room, bias: RoomPosition = pos, avoidStorage: Boolean = false,
                               takeFromCarriers: Int = -1): StoreOwner? {

    if (takeFromCarriers > 0) {
        // Else if there is a carrier with enough energy NEARBY, use that one instead
        val carrierCreep = room.bySort(
            FIND_MY_CREEPS,
            f = hasSomeEnergy and hasRole(Role.CARRIER) and withinRange(takeFromCarriers, pos),
            sort = byEnoughEnergy(store.getFreeCapacity() / 2) then byDistance(bias) then byMostUsed
        )
        if (carrierCreep != null) return carrierCreep.unsafeCast<StoreOwner>()
    }

    val preference: Array<StructureConstant> = if (avoidStorage) arrayOf(STRUCTURE_CONTAINER)
    else arrayOf(STRUCTURE_CONTAINER, STRUCTURE_STORAGE)
    // If there is a container with enough energy, use it
    val harvesterContainer = room.bySort(
        preference,
        f = hasSomeEnergy and notControllerBuffer,
        sort = byEnoughEnergy(store.getFreeCapacity() / 2) then byDistance(
            bias,
            3
        ) then byOrderIn(preference) then byMostUsed
    )

    if (harvesterContainer != null) return harvesterContainer.unsafeCast<StoreOwner>()

    // Else if there is a harvester with enough energy, use that one instead
    val harvesterCreep = room.bySort(
        FIND_MY_CREEPS,
        f = hasSomeEnergy and hasRole(Role.HARVESTER, Role.OUTER_HARVESTER),
        sort = byEnoughEnergy(store.getFreeCapacity() / 2) then byDistance(bias, 5) then byMostUsed
    )

    return harvesterCreep.unsafeCast<StoreOwner>()
}


fun Creep.findCaravanPickupEnergy(room: Room = this.room, bias: RoomPosition = pos): StoreOwner? {

    // If there is a container with enough energy, use it
    val harvesterContainer = room.bySort(STRUCTURE_CONTAINER,
        f = hasSomeEnergy and hasTag(SOURCE_BUFFER),
        sort = byEnoughEnergy(store.getFreeCapacity()) then byDistance(bias, 3)
                then byDistance(room.controller!!.pos).reversed() then byMostUsed)

    return harvesterContainer.unsafeCast<StoreOwner>()

}


fun Creep.findConvenientContainer(avoidTags: Array<String>): Structure? {
    val preferredStructures: Array<StructureConstant> = arrayOf(STRUCTURE_TOWER, STRUCTURE_STORAGE, STRUCTURE_CONTAINER)
    return room.bySort(preferredStructures,
        f = hasNoEnergy and hasNoTag(*avoidTags),
        sort = byEnoughSpace(store.getUsedCapacity()) then byDistance(pos, 10) then byOrderIn(preferredStructures))
    .unsafeCast<Structure>()
}

/*fun Creep.findCriticalRepairTarget(): Structure? {
    return room.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < 650 } })
        .sortedBy { it.pos.getRangeTo(myRepairPriority()) }.minByOrNull { it.hits }
}*/

fun Creep.findMilitaryRepairTarget(maxHits: Int = 10000, bias: RoomPosition = pos): Structure? {
    val preferredStructures: Array<StructureConstant> = arrayOf(STRUCTURE_RAMPART, STRUCTURE_TOWER, STRUCTURE_WALL)
    return room.bySort(preferredStructures,
        f = lessThanMaxHits and lessHitsThan(maxHits),
        sort = byOrderIn(preferredStructures) then byBorderProximity then byHits then byDistance(bias, 5))
        ?.unsafeCast<Structure>()
}

fun Creep.findInfrastructureRepairTarget(maxHits: Int = 10000, bias: RoomPosition = pos): Structure? {
    val preferredStructures: Array<StructureConstant> = arrayOf(STRUCTURE_ROAD)
    return room.bySort(preferredStructures,
        f = lessThanMaxHits and lessHitsThan(maxHits),
        sort = byOrderIn(preferredStructures) then byMaxHits then { it.hits/1000 } then byDistance(bias))
        ?.unsafeCast<Structure>()
}

val repairStructureFilter = lessThanMaxHits and lessHitsThan(10000) or
        (isType(STRUCTURE_CONTAINER) and lessHitsThan(30000))

fun Room.findTowerRepairTargets(n: Int): Array<Structure> {
    return this.bySortN(n, arrayOf(FIND_STRUCTURES),
        f = repairStructureFilter,
         sort = byTrueFirst(lessHitsThan(500)) then
                byTrueFirst(lessHitsThan<Structure>(30000) and isType(STRUCTURE_CONTAINER)) then
                byTrueFirst(lessHitsThan(5000)) then
                byTrueFirst(lessHitsThan<Structure>(10000) and isType(STRUCTURE_RAMPART)) then
                byTrueFirst(lessHitsThan<Structure>(10000) and isType(STRUCTURE_WALL)) then
                byHits)
}

val _cachedRepairTargetIds = mutableMapOf<String,MutableList<String>>()
val _lastSearch = mutableMapOf<String, Int>()

fun Room.getTowerRepairTarget(): Structure? {
    while (_cachedRepairTargetIds.containsKey(this.name) && _cachedRepairTargetIds[this.name] != null &&
            _cachedRepairTargetIds[this.name]!!.isNotEmpty()) {
        val id = _cachedRepairTargetIds[this.name]!!.first()
        val structure = Game.getObjectById<Structure>(id)
        if (structure == null || !repairStructureFilter(structure)) {
            _cachedRepairTargetIds[this.name]!!.remove(id)
            continue
        }
        return structure
    }
    if (_lastSearch.contains(this.name) && Game.time - _lastSearch[this.name]!! < 10) return null
    console.log("Searching for new repair targets in ${this.name}")
    _lastSearch[this.name] = Game.time
    val targets = this.findTowerRepairTargets(10)
    _cachedRepairTargetIds[this.name] = targets.map { it.id }.toMutableList()
    return targets.firstOrNull()
}


val SOURCE_BUFFER = "SOURCE_BUFFER"
val CONTROLLER_BUFFER = "CONTROLLER_BUFFER"
val MINERAL_BUFFER = "MINERAL_BUFFER"
val TOWER_BUFFER = "TOWER_BUFFER"
val GENERIC_CONTAINER = "GENERIC_CONTAINER"


fun Structure.getTags(): Array<String> {
    return this.room.memory.structureTags.filter { it.first == this.id }.map { it.second }.toTypedArray()
}

fun Structure.hasTag(tag: String): Boolean {
    return this.room.memory.structureTags.any { it.first == this.id && it.second == tag }
}

fun Structure.addTag(tag: String) {
    if (hasTag(tag)) return
    this.room.memory.structureTags += Pair(this.id, tag)
}

fun Structure.removeTag(tag: String) {
    this.room.memory.structureTags = this.room.memory.structureTags
        .filter { it.first != this.id || it.second != tag }.toTypedArray()
}

fun Room.tagContainers() {
    //f = { it: StructureContainer -> it.getTags().isEmpty() }
    val containers = this.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER &&
            it.unsafeCast<StructureContainer>().getTags().isEmpty() } })
        .unsafeCast<Array<StructureContainer>>()
    for (container in containers) {
        if (container.pos.findInRange(FIND_SOURCES, 2).isNotEmpty()) {
            container.addTag(SOURCE_BUFFER)
        }
        if (container.pos.findInRange(FIND_MINERALS, 2).isNotEmpty()) {
            container.addTag(MINERAL_BUFFER)
        }
        if (this.controller != null && container.pos.inRangeTo(this.controller!!.pos, 3)) {
            container.addTag(CONTROLLER_BUFFER)
        }
        if (container.pos.findInRange(FIND_MY_STRUCTURES, 2, options { filter = { it.structureType == STRUCTURE_TOWER } }).isNotEmpty()) {
            container.addTag(TOWER_BUFFER)
        }
        if (container.getTags().isEmpty()) {
            container.addTag(GENERIC_CONTAINER)
        }
    }

}


// TESTING
fun test() {
    Game.rooms.values[0].byOrder(FIND_CREEPS)
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = { it.hits < it.hitsMax })
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = hasNoEnergy)
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = hasNoEnergy and hasSomeEnergy)
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = hasNoEnergy or hasSomeEnergy)

    Game.rooms.values[0].bySort(FIND_CREEPS, sort = { it.hits })
    Game.rooms.values[0].bySort(FIND_CREEPS, f = { it.hits < it.hitsMax }, sort = { it.hits })


    val sortingBy = { it: Creep -> it.name } then { it.hits } then { it.memory.role } then byDistance(Game.rooms.values[0].controller!!.pos)


    Game.rooms.values[0]
        .bySort(FIND_CREEPS, f = { it: Creep -> it.hits < it.hitsMax } and { !it.spawning } and lessThanMaxHits,
                             sort = { it: Creep -> it.name } then { it.hitsMax } )


    Game.rooms.values[0].byOrder(FIND_STRUCTURES, f = { it.isType(STRUCTURE_CONTAINER) } )
    Game.rooms.values[0].byOrder(FIND_STRUCTURES, f = { it.isType(STRUCTURE_CONTAINER, STRUCTURE_ROAD) } )

    Game.rooms.values[0].byOrder(FIND_STRUCTURES, f = isType(STRUCTURE_CONTAINER) and hasNoEnergy )

    Game.rooms.values[0].byOrder(STRUCTURE_CONTAINER, f = hasNoEnergy)



    val array = arrayOf(Pair(1,"b"), Pair(2,"a"), Pair(1,"a"), Pair(2,"b"), Pair(3,"a"))

    val selector = { it: Pair<Int, String> -> it.first } then { it: Pair<Int, String> -> it.second }
    val selector2 = { it: Pair<Int, String> -> it.second } then { it: Pair<Int, String> -> it.first }

    val sorted = array.sortedBy(selector)
    console.log(sorted)
    val sorted2 = array.sortedBy(selector2)
    console.log(sorted2)
}



