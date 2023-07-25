package bjnick

import role
import screeps.api.*
import screeps.api.structures.Structure

// Define filter lambda type
typealias Filter<T> = (T) -> Boolean

// SPECIFIC TO ENERGY
val hasFreeCapacity: Filter<Any> = { owner -> owner.unsafeCast<StoreOwner>()
    .store.getFreeCapacity(RESOURCE_ENERGY) > 0 }

val hasUsedCapacity: Filter<Any> = { owner -> owner.unsafeCast<StoreOwner>()
    .store.getUsedCapacity(RESOURCE_ENERGY) > 0 }

fun Structure.isType(vararg types: StructureConstant): Boolean = this.structureType in types
// Filter of the above
fun isType(vararg types: StructureConstant): Filter<Structure> = { it.structureType in types }
fun isType(types: Array<StructureConstant>): Filter<Structure> = { it.structureType in types }

val lessThanMaxHits: Filter<Attackable> = { structure: Attackable -> structure.hits < structure.hitsMax }
fun <T: Attackable> lessHitsThan(hits: Int): Filter<T> = { structure: T -> structure.hits < hits }


// Define an "and" operator for filters
infix fun <T> Filter<T>.and(other: Filter<T>): Filter<T> = { this(it) && other(it) }

// Define an "or" operator for filters
infix fun <T> Filter<T>.or(other: Filter<T>): Filter<T> = { this(it) || other(it) }

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
fun byDistance(to: RoomPosition): Sorter<HasPosition, Int> = { it.pos.getRangeTo(to) }
val byHits: Sorter<Structure, Int> = { it.hits }

val byLeastFree: Sorter<StoreOwner, Int> = { it.store.getFreeCapacity(RESOURCE_ENERGY) ?: 0 }
val byLeastUsed: Sorter<StoreOwner, Int> = { it.store.getUsedCapacity(RESOURCE_ENERGY) ?: 0 }

val byMostFree = byLeastFree.reversed()
val byMostUsed = byLeastUsed.reversed()


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
    return byOrder(arrayOf(FIND_STRUCTURES), isType(structureConstants) and f)
}

fun Room.byOrder(structureConstant: StructureConstant, f: Filter<Structure> = { _: Structure -> true } ): dynamic {
    return byOrder(arrayOf(structureConstant), f)
}

/**
 * Takes in any number of find constants and returns an object sorted by the specified order
 */
fun <T, R: Comparable<R>> Room.bySort(findConstants: Array<FindConstant<T>>, f: Filter<T> = { _: T -> true }, sortBy: Sorter<T, R>): dynamic {
    val results = mutableListOf<T>()
    for (findConstant in findConstants) {
        results.addAll(this.find(findConstant, options { filter = f }))
    }
    return results.minByOrNull(sortBy)
}

fun <T, R: Comparable<R>> Room.bySort(findConstant: FindConstant<T>, f: Filter<T> = { _: T -> true }, sortBy: Sorter<T, R>): dynamic {
    return bySort(arrayOf(findConstant), f, sortBy)
}




fun test() {
    Game.rooms.values[0].byOrder(FIND_CREEPS)
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = { it.hits < it.hitsMax })
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = hasFreeCapacity)
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = hasFreeCapacity and hasUsedCapacity)
    Game.rooms.values[0].byOrder(FIND_CREEPS, f = hasFreeCapacity or hasUsedCapacity)

    Game.rooms.values[0].bySort(FIND_CREEPS, sortBy = { it.hits })
    Game.rooms.values[0].bySort(FIND_CREEPS, f = { it.hits < it.hitsMax }, sortBy = { it.hits })


    val sortingBy = { it: Creep -> it.name } then { it.hits } then { it.memory.role } then byDistance(Game.rooms.values[0].controller!!.pos)


    Game.rooms.values[0]
        .bySort(FIND_CREEPS, f = { it: Creep -> it.hits < it.hitsMax } and { !it.spawning } and lessThanMaxHits,
                             sortBy = { it: Creep -> it.name } then { it.hitsMax } )


    Game.rooms.values[0].byOrder(FIND_STRUCTURES, f = { it.isType(STRUCTURE_CONTAINER) } )
    Game.rooms.values[0].byOrder(FIND_STRUCTURES, f = { it.isType(STRUCTURE_CONTAINER, STRUCTURE_ROAD) } )

    Game.rooms.values[0].byOrder(FIND_STRUCTURES, f = isType(STRUCTURE_CONTAINER) and hasFreeCapacity )

    Game.rooms.values[0].byOrder(STRUCTURE_CONTAINER, f = hasFreeCapacity)



    val array = arrayOf(Pair(1,"b"), Pair(2,"a"), Pair(1,"a"), Pair(2,"b"), Pair(3,"a"))

    val selector = { it: Pair<Int, String> -> it.first } then { it: Pair<Int, String> -> it.second }
    val selector2 = { it: Pair<Int, String> -> it.second } then { it: Pair<Int, String> -> it.first }

    val sorted = array.sortedBy(selector)
    console.log(sorted)
    val sorted2 = array.sortedBy(selector2)
    console.log(sorted2)
}



