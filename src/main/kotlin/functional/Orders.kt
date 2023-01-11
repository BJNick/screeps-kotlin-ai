package functional

import screeps.api.*
import targetID
import targetTask

interface Order {
    val priority: Int
}

class SourceOrder(val source: Source, val numberOfCreeps: Int, override val priority: Int) : Order

class ConstructionOrder(val site: ConstructionSite, override val priority: Int) : Order

class SpawnOrder(val spawnCommand: SpawnCommand, override val  priority: Int) : Order


open class FeedOrder (
    open val freeCapacity : Int,
    open val targetID: String,
    override val priority: Int,
) : Order {
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
}

class ConcreteFeedOrder(storeOwner: StoreOwner, override val priority: Int) : FeedOrder(
    storeOwner.store.getFreeCapacity(),
    storeOwner.id,
    priority
)


fun <T : Order> getTopNOrders(orders: List<T>, n: Int): List<T> {
    return orders.sortedBy { it.priority }.take(n)
}

fun filterOutFull(listOfOrders: List<FeedOrder>): List<FeedOrder> {
    return listOfOrders.filter { it.freeCapacity > 0 }
}

interface CarryingEnergy {
    val energyCarried : Int
}

val Creep.energyCarried : Int
    get() = this.store[RESOURCE_ENERGY] ?: 0

fun <T : CarryingEnergy> filterOutEmpty(listOfCreeps: List<T>): List<T> {
    return listOfCreeps.filter { it.energyCarried > 0 }
}

fun <T : CarryingEnergy> assignCreepsToFeed(creepList: List<T>, orders: List<FeedOrder>): List<Pair<T, FeedOrder>> {
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

open class AssignedFeedTask(
    val targetID : String,
    val energyCarried : Int,
) {
    override fun toString(): String {
        return "AssignedFeedTask(targetID='$targetID', energyCarried=$energyCarried)"
    }
}

class CreepAssignedFeedTask(creep: Creep) : AssignedFeedTask(
    creep.memory.targetID,
    creep.energyCarried
)

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

