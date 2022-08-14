package starter

import screeps.api.*
import screeps.api.structures.StructureController


enum class Role {
    UNASSIGNED,
    SETTLER,
    HARVESTER,
    BUILDER,
    UPGRADER,
}

fun Creep.settle() {
    if (isCollecting()) {
        collectEnergy()
    } else {
        putEnergy(findEnergyTarget(room))
    }
}

fun Creep.upgrade(controller: StructureController) {
    if (!memory.collecting && store[RESOURCE_ENERGY] == 0) {
        memory.collecting = true
        say("🔄 harvest")
    }
    if (memory.collecting && store[RESOURCE_ENERGY] == store.getCapacity()) {
        memory.collecting = false
        say("⬆ upgrade")
    }

    if (memory.collecting) {
        val sources = room.find(FIND_SOURCES)
        if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
            moveTo(sources[0].pos)
        }
    } else {
        if (upgradeController(controller) == ERR_NOT_IN_RANGE) {
            moveTo(controller.pos)
        }
    }
}

fun Creep.pause() {
    if (memory.pause < 10) {
        //blink slowly
        if (memory.pause % 3 != 0) say("\uD83D\uDEAC")
        memory.pause++
    } else {
        memory.pause = 0
        memory.role = Role.HARVESTER
    }
}

fun Creep.build(assignedRoom: Room = this.room) {
    if (!memory.collecting && store[RESOURCE_ENERGY] == 0) {
        memory.collecting = true
        say("🔄 harvest")
    }
    if (memory.collecting && store[RESOURCE_ENERGY] == store.getCapacity()) {
        memory.collecting = false
        say("🚧 build")
    }

    if (!memory.collecting) {
        val targets = assignedRoom.find(FIND_MY_CONSTRUCTION_SITES)
        if (targets.isNotEmpty()) {
            if (build(targets[0]) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        }
    } else {
        val sources = room.find(FIND_SOURCES)
        if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
            moveTo(sources[0].pos)
        }
    }
}

fun Creep.harvest(fromRoom: Room = this.room, toRoom: Room = this.room) {
    if (store[RESOURCE_ENERGY] < store.getCapacity()) {
        val sources = fromRoom.find(FIND_SOURCES)
        if (harvest(sources[0]) == ERR_NOT_IN_RANGE) {
            moveTo(sources[0].pos)
        }
    } else {
        val targets = toRoom.find(FIND_STRUCTURES)
            .filter { (it.structureType == STRUCTURE_EXTENSION || it.structureType == STRUCTURE_SPAWN) }
            .map { it.unsafeCast<StoreOwner>() }
            //.also { console.log(it.map { it.store[RESOURCE_ENERGY].toString() + " out of " + it.store.getCapacity(RESOURCE_ENERGY) }.joinToString()) }
            .filter { it.store[RESOURCE_ENERGY] < it.store.getCapacity(RESOURCE_ENERGY) }

        if (targets.isNotEmpty()) {
            if (transfer(targets[0], RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        } else {
            say("No dest")
        }
    }
}
