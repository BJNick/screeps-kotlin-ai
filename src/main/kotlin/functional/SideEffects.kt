package functional

import screeps.api.*
import targetID


/** Execute - side effect function */
fun executeSpawn(spawn: Spawn, command: SpawnCommand) {
    if (DEBUG_MODE) {
        println("DEBUG Spawn: Creating ${command.name} with body ${command.body} at ${spawn.id}")
        return
    }
    spawn.structure?.spawnCreep(asBodyConstants(command.body), command.name, command.spawnOptions)
    if (spawn.structure == null) console.log("Warning: Spawn structure is null")
}


fun executeFeedOrder(carrier: CreepCarryingEnergy, order: FeedOrder) {
    if (DEBUG_MODE) {
        println("DEBUG FeedOrder: $carrier feeding ${carrier.energyCarried} to ${order.targetID} with ${order.freeCapacity}")
        return
    }
    val target: StoreOwner? = Game.getObjectById(order.targetID)
    val creep: Creep? = carrier.creep
    if (target == null || creep == null) {
        console.log("Warning: Feed target structure or creep is null")
        return
    }
    creep.memory.targetID = order.targetID
}


fun orderToConcrete(carrier: CreepCarryingEnergy, order: FeedOrder, action: (creep: Creep, target: StoreOwner) -> Any) {
    val target: StoreOwner? = Game.getObjectById(order.targetID)
    val creep: Creep? = carrier.creep
    if (target == null || creep == null) {
        console.log("Warning: Feed target structure or creep is null")
        return
    }
    action(creep, target);
}


fun executeOrMove(f: () -> ScreepsReturnCode, move: () -> ScreepsReturnCode) {
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
            { creep.transfer(target, RESOURCE_ENERGY) },
            { creep.moveTo(target) }
        )
    }
}

