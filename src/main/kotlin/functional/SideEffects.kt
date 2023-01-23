package functional

import screeps.api.Creep
import screeps.api.Game
import screeps.api.StoreOwner
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
