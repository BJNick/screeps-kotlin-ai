package functional

import screeps.api.*
import targetID
import targetTask


interface HasPriority {
    val priority: Int
}

interface FeedTarget {
    val id: String
    val freeCapacity: Int
}

interface CollectTarget {
    val id: String
    val availableEnergy: Int
}

/** An abstraction of a creep carrying some energy */
open class CreepCarryingEnergy(val creep: Creep? = null, energyCarried: Int? = null, energyCapacity: Int? = null) {
    open val energyCarried : Int = creep?.store?.getUsedCapacity(RESOURCE_ENERGY) ?: energyCarried ?: 50
    open val energyCapacity : Int = creep?.store?.getCapacity(RESOURCE_ENERGY) ?: energyCapacity ?: 100
    open val spaceAvailable : Int = (energyCapacity ?: 100) - (energyCarried ?: 0)
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

/** A generic order to move energy from one place to another */
open class MoveOrder (
    val targetID: String,
    override val priority: Int,
) : HasPriority {
    override fun toString(): String =
        "Order(targetID='$targetID', priority=$priority)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as MoveOrder
        if (targetID != other.targetID) return false
        if (priority != other.priority) return false
        return true
    }
    override fun hashCode(): Int {
        var result = targetID.hashCode()
        result = 31 * result + priority
        return result
    }
}

/** An order to feed the target with energy, up until its freeCapacity */
open class FeedOrder (
    val freeCapacity : Int,
    targetID: String,
    priority: Int,
) : MoveOrder(targetID, priority) {
    override fun toString(): String =
        "FeedOrder(freeCapacity=$freeCapacity, targetID='$targetID', priority=$priority)"
}

/** An order to collect energy from the target, up until its availableEnergy */
open class CollectOrder (
    val availableEnergy : Int,
    targetID: String,
    priority: Int,
) : MoveOrder(targetID, priority) {
    override fun toString(): String =
        "CollectOrder(availableEnergy=$availableEnergy, targetID='$targetID', priority=$priority)"
}

/** Creates a feed order from the store owner */
fun feedOrderFromStoreOwner(feedTarget: FeedTarget, priority: Int) = FeedOrder(
    feedTarget.freeCapacity,
    feedTarget.id,
    priority
)

/** Creates a collect order from the store owner */
fun collectOrderFromStoreOwner(collectTarget: CollectTarget, priority: Int) = CollectOrder(
    collectTarget.availableEnergy,
    collectTarget.id,
    priority
)

/** A previously assigned task to move energy */
open class AssignedTask(
    val targetID : String,
    val creep : CreepCarryingEnergy,
) {
    override fun toString(): String {
        return "AssignedFeedTask(targetID='$targetID', energyCarried=$creep.energyCarried, energyCapacity=$creep.energyCapacity)"
    }
}

/** Creates a feed task from a creep's memory */
class CreepAssignedFeedTask(creep: CreepWithTask) : AssignedTask(
    creep.targetID,
    creep
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

/** Filters out empty containers from collect orders */
fun filterOutEmpty(listOfOrders: List<CollectOrder>): List<CollectOrder> {
    return listOfOrders.filter { it.availableEnergy > 0 }
}

/** Filters out creeps with empty inventories */
fun <T : CreepCarryingEnergy> filterOutEmpty(listOfCreeps: List<T>): List<T> {
    return listOfCreeps.filter { it.energyCarried > 0 }
}

/** Filters out creeps with full inventories */
fun <T : CreepCarryingEnergy> filterOutFull(listOfCreeps: List<T>): List<T> {
    return listOfCreeps.filter { it.spaceAvailable > 0 }
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

/** Assigns a list of creeps with energy to collect orders, considering the priority and capacity  */
fun <T : CreepCarryingEnergy> assignCreepsToCollect(creepList: List<T>, orders: List<CollectOrder>): List<Pair<T, CollectOrder>> {
    val ordersSortedByPriority = orders.sortedBy { it.priority }
    val creepListSortedBySpaceAvailable = filterOutFull(creepList).sortedBy { it.spaceAvailable }.reversed()
    // Assign creeps to an order until it fills up, then move on to next order
    // NOT FUNCTIONAL PROGRAMMING
    val assignments = mutableListOf<Pair<T, CollectOrder>>()
    val creepsLeft = mutableListOf<T>()
    creepsLeft.addAll(creepListSortedBySpaceAvailable)
    for (order in ordersSortedByPriority) {
        var energyLeft = order.availableEnergy
        while (creepsLeft.isNotEmpty() && energyLeft > 0) {
            val creep = creepsLeft.removeAt(0)
            assignments.add(Pair(creep, order))
            energyLeft -= creep.spaceAvailable;
        }
    }
    // Return an immutable list
    return assignments.toList()
}

/** Creates a new list of orders without previously assigned tasks */
fun subtractAlreadyAssigned(orders: List<FeedOrder>, assigned: List<AssignedTask>): List<FeedOrder> {
    val assignedMap = assigned.groupBy { it.targetID }
    return orders.mapNotNull { order ->
        val assignedToThisOrder = assignedMap[order.targetID] ?: listOf()
        val assignedEnergy = assignedToThisOrder.sumOf { it.creep.energyCarried }
        val newFreeCapacity = order.freeCapacity - assignedEnergy

        if (newFreeCapacity > 0)
            FeedOrder(newFreeCapacity, order.targetID, order.priority)
        else
            null
    }
}

/** Creates a new list of orders without previously assigned tasks */
fun subtractAlreadyAssigned(orders: List<CollectOrder>, assigned: List<AssignedTask>): List<CollectOrder> {
    val assignedMap = assigned.groupBy { it.targetID }
    return orders.mapNotNull { order ->
        val assignedToThisOrder = assignedMap[order.targetID] ?: listOf()
        val assignedSpace = assignedToThisOrder.sumOf { it.creep.spaceAvailable }
        val newAvailableEnergy = order.availableEnergy - assignedSpace

        if (newAvailableEnergy > 0)
            CollectOrder(newAvailableEnergy, order.targetID, order.priority)
        else
            null
    }
}



