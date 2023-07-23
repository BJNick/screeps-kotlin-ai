package bjnick

import assignedSource
import collecting
import role
import screeps.api.*

object Role {
    val UNASSIGNED = "UNASSIGNED"
    val SETTLER = "SETTLER"
    val CARRIER = "CARRIER"
    val HARVESTER = "HARVESTER"
    val BUILDER = "BUILDER"
    val UPGRADER = "UPGRADER"
}

fun Creep.executeRole() {
    if (this.spawning) return
    when (memory.role) {
        Role.UNASSIGNED -> console.log("Unassigned role: $name")
        Role.SETTLER -> settler()
        Role.CARRIER -> carrier()
        Role.HARVESTER -> harvester()
        Role.BUILDER -> builder()
        Role.UPGRADER -> upgrader()
    }
}

fun Creep.settler() {
    if (isCollecting()) {
        // collectFromASource() // TODO repurposed settlers
        if (this.memory.assignedSource != "") unassignSource(name)
        this.memory.role = "BUILDER"
    } else {
        //if (!ProgressState.carriersPresent)
        putEnergy(findEnergyTarget(room))
    }
}


fun Creep.carrier() {
    if (!stackToCarriers())
    if (isCollecting()) {
        findDroppedEnergy(room)?.let {
            collectFrom(it); return
        }
        collectFromHarvesters()
    } else {
        putEnergy(findEnergyTarget(room))
    }
}

fun Creep.harvester() {
    if (isCollecting()) {
        collectFromASource()
    } else {
        if (!ProgressState.carriersPresent)
            putEnergy(findEnergyTarget(room))
        else if (!memory.collecting && store[RESOURCE_ENERGY] < store.getCapacity())
            memory.collecting = true // keep collecting even if not fully emptied
    }
}

fun Creep.builder() {
    if (isCollecting()) {
        //if (!ProgressState.carriersPresent)
        collectFromClosest() // TODO
    } else {
        putEnergy(getConstructionSites(room).firstOrNull())
    }
}

fun Creep.upgrader() {
    if (isCollecting()) {
        //if (!ProgressState.carriersPresent)
        collectFromClosest() // TODO
    } else {
        putEnergy(room.controller)
    }
}
