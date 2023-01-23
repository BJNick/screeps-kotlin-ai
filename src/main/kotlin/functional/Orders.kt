package functional

import screeps.api.*
import targetID



interface HasPriority {
    val priority: Int
}

interface FeedTarget {
    val id: String
    val freeCapacity: Int
}

/** An abstraction of a creep carrying some energy */
open class CreepCarryingEnergy(val creep: Creep? = null, private val _energyCarried: Int? = null) {
    open val energyCarried : Int = creep?.store?.getUsedCapacity(RESOURCE_ENERGY) ?: _energyCarried ?: 0
    override fun toString(): String = "CreepCarryingEnergy($energyCarried)"
}

/** An abstraction of a creep carrying some energy and having a targetID feed task */
open class CreepWithTask(creep: Creep? = null): CreepCarryingEnergy(creep) {
    open val targetID: String = creep?.memory?.targetID ?: "debugTargetID"
}


/** An order to surround the source with miners, up to the numberOfCreeps */
class SourceOrder(val source: Source, val numberOfCreeps: Int, override val priority: Int) : HasPriority

/** ??? not sure if this is needed after FeedOrder */
class ConstructionOrder(val site: ConstructionSite, override val priority: Int) : HasPriority

/** An order to execute a creep spawn command */
class SpawnOrder(val spawnCommand: SpawnCommand, override val  priority: Int) : HasPriority


/** An order to feed the target with energy, up until its freeCapacity */
open class FeedOrder (
    val freeCapacity : Int,
    val targetID: String,
    override val priority: Int,
) : HasPriority {
    override fun toString(): String {
        return "FeedOrder(freeCapacity=$freeCapacity, targetID='$targetID', priority=$priority)"
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as FeedOrder
        if (freeCapacity != other.freeCapacity) return false
        if (targetID != other.targetID) return false
        if (priority != other.priority) return false
        return true
    }
    override fun hashCode(): Int {
        var result = freeCapacity
        result = 31 * result + targetID.hashCode()
        result = 31 * result + priority
        return result
    }
}

/** Creates a feed order from the store owner */
class ConcreteFeedOrder(feedTarget: FeedTarget, priority: Int) : FeedOrder(
    feedTarget.freeCapacity,
    feedTarget.id,
    priority
)

/** A previously assigned task to feed the target */
open class AssignedFeedTask(
    val targetID : String,
    val energyCarried : Int,
) {
    override fun toString(): String {
        return "AssignedFeedTask(targetID='$targetID', energyCarried=$energyCarried)"
    }
}

/** Creates a feed task from a creep's memory */
class CreepAssignedFeedTask(creep: CreepWithTask) : AssignedFeedTask(
    creep.targetID,
    creep.energyCarried
)


/** Sort by priority */
fun <T : HasPriority> sortByPriority(listOfOrders: List<T>): List<T> {
    return listOfOrders.sortedBy { -it.priority } // descending
}

/** Sort by priority and return top N */
fun <T : HasPriority> getTopNOrders(orders: List<T>, n: Int): List<T> {
    return sortByPriority(orders).take(n)
}

/** Filters out full containers from feed orders */
fun filterOutFull(listOfOrders: List<FeedOrder>): List<FeedOrder> {
    return listOfOrders.filter { it.freeCapacity > 0 }
}

/** Filters out creeps with empty inventories */
fun <T : CreepCarryingEnergy> filterOutEmpty(listOfCreeps: List<T>): List<T> {
    return listOfCreeps.filter { it.energyCarried > 0 }
}


/** Assigns a list of creeps with energy to feed orders, considering the priority and capacity  */
fun <T : CreepCarryingEnergy> assignCreepsToFeed(creepList: List<T>, orders: List<FeedOrder>): List<Pair<T, FeedOrder>> {
    val ordersSortedByPriority = orders.sortedBy { it.priority }
    val creepListSortedByEnergyCarried = filterOutEmpty(creepList).sortedBy { it.energyCarried }.reversed()
    // Assign creeps to an order until it fills up, then move on to next order
    // NOT FUNCTIONAL PROGRAMMING
    val assignments = mutableListOf<Pair<T, FeedOrder>>()
    val creepsLeft = mutableListOf<T>()
    creepsLeft.addAll(creepListSortedByEnergyCarried)
    for (order in ordersSortedByPriority) {
        var capacityLeft = order.freeCapacity
        while (creepsLeft.isNotEmpty() && capacityLeft > 0) {
            val creep = creepsLeft.removeAt(0)
            assignments.add(Pair(creep, order))
            capacityLeft -= creep.energyCarried;
        }
    }
    // Return an immutable list
    return assignments.toList()
}

/** Creates a new list of orders without previously assigned tasks */
fun subtractAlreadyAssigned(orders: List<FeedOrder>, assigned: List<AssignedFeedTask>): List<FeedOrder> {
    val assignedMap = assigned.groupBy { it.targetID }
    return orders.mapNotNull { order ->
        val assignedToThisOrder = assignedMap[order.targetID] ?: listOf()
        val assignedEnergy = assignedToThisOrder.sumOf { it.energyCarried }
        val newFreeCapacity = order.freeCapacity - assignedEnergy

        if (newFreeCapacity > 0)
            FeedOrder(newFreeCapacity, order.targetID, order.priority)
        else
            null
    }
}

