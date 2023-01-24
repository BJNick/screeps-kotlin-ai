package functional

import screeps.api.*
import screeps.api.structures.StructureController
import targetID
import targetTask

val FEED_TASK = "FEED_TASK"
val COLLECT_TASK = "COLLECT_TASK"

/** Execute - side effect function */
fun executeSpawn(spawn: Spawn, command: SpawnCommand) {
    if (DEBUG_MODE) {
        println("DEBUG Spawn: Creating ${command.name} with body ${command.body} at ${spawn.id}")
        return
    }
    spawn.structure?.spawnCreep(asBodyConstants(command.body), command.name, command.spawnOptions)
    if (spawn.structure == null) console.log("Warning: Spawn structure is null")
}


fun assignFeedOrder(carrier: CreepCarryingEnergy, order: FeedOrder) {
    if (DEBUG_MODE) {
        println("DEBUG FeedOrder: $carrier feeding ${carrier.energyCarried} to ${order.targetID} with ${order.freeCapacity}")
        return
    }
    val target: HasPosition? = Game.getObjectById(order.targetID)
    val creep: Creep? = carrier.creep
    if (target == null || creep == null) {
        console.log("Warning: FeedOrder target or creep is null")
        return
    }
    creep.memory.targetID = order.targetID
    creep.memory.targetTask = FEED_TASK
}

fun assignCollectOrder(carrier: CreepCarryingEnergy, order: CollectOrder) {
    if (DEBUG_MODE) {
        println("DEBUG CollectOrder: $carrier collecting ${carrier.spaceAvailable} from ${order.targetID} with ${order.availableEnergy}")
        return
    }
    val target: HasPosition? = Game.getObjectById(order.targetID)
    val creep: Creep? = carrier.creep
    if (target == null || creep == null) {
        console.log("Warning: CollectOrder target or creep is null")
        return
    }
    creep.memory.targetID = order.targetID
    creep.memory.targetTask = COLLECT_TASK
}


fun orderToConcrete(carrier: CreepCarryingEnergy, order: MoveOrder, action: (creep: Creep, target: StoreOwner) -> Any) {
    val target: StoreOwner? = Game.getObjectById(order.targetID)
    val creep: Creep? = carrier.creep
    if (target == null || creep == null) {
        console.log("Warning: Feed target structure or creep is null")
        return
    }
    action(creep, target);
}


fun executeOrMove(f: () -> ScreepsReturnCode?, move: () -> ScreepsReturnCode) {
    if (DEBUG_MODE) {
        println("DEBUG executeOrMove: executing $f or moving $move")
        return
    }
    if (f() == ERR_NOT_IN_RANGE) {
        move()
    }
}


fun executeTransfer(carrier: CreepCarryingEnergy, order: FeedOrder) {
    orderToConcrete(carrier, order) { creep, target ->
        executeOrMove(
            { putEnergyMethod(target)(creep) },
            { creep.moveTo(target) }
        )
    }
}

fun executeCollection(carrier: CreepCarryingEnergy, order: CollectOrder) {
    orderToConcrete(carrier, order) { creep, target ->
        executeOrMove(
            { collectFromMethod(target)(creep) },
            { creep.moveTo(target) }
        )
    }
}


fun logError(message: Any?) : Unit = if (DEBUG_MODE) println(message) else console.log(message)

/** Returns a procedure appropriate for the target type for putting energy into */
fun putEnergyMethod(target: HasPosition?): (creep: Creep) -> ScreepsReturnCode? {
    if (target == null) return { _ -> logError("putEnergy: target is null"); null }
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    return when (target) {
        is StructureController -> { creep -> creep.upgradeController(target) }
        is ConstructionSite    -> { creep -> creep.build(target) }
        is Creep               -> { creep -> creep.transfer(target, RESOURCE_ENERGY) }
        else -> if (target as? StoreOwner != null)   { creep -> creep.transfer(target, RESOURCE_ENERGY) }
        else { _ -> logError("putEnergy: target $target is not a Structure, Creep or ConstructionSite"); null }
    }
}

/** Returns a procedure appropriate for the target type for collecting energy from */
fun collectFromMethod(target: HasPosition?): (creep: Creep) -> ScreepsReturnCode? {
    if (target == null) return { _ -> logError("collectFrom: target is null"); null }
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    return when (target) {
        is Source    -> { creep -> creep.harvest(target) }
        is Tombstone -> { creep -> creep.withdraw(target, RESOURCE_ENERGY) }
        is Creep     -> { creep -> target.transfer(creep, RESOURCE_ENERGY) }
        is Resource  -> { creep -> creep.pickup(target) }
        else -> if (target as? StoreOwner != null)  { creep -> creep.withdraw(target, RESOURCE_ENERGY) } else
            return { creep -> logError("collectFrom: target $target is of unsupported type"); null }
    }
}
