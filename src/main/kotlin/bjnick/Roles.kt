package bjnick

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
        collectFromASource()
    } else {
        if (!ProgressState.carriersPresent)
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
    }
}

fun Creep.builder() {
    if (isCollecting()) {
        if (!ProgressState.carriersPresent)
            collectFromASource() // TODO
    } else {
        putEnergy(getConstructionSites(room).firstOrNull())
    }
}

fun Creep.upgrader() {
    if (isCollecting()) {
        if (!ProgressState.carriersPresent)
            collectFromASource() // TODO
    } else {
        putEnergy(room.controller)
    }
}
