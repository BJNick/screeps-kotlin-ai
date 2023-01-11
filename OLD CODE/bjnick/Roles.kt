package bjnick

import screeps.api.*

enum class Role {
    UNASSIGNED,
    SETTLER,
    CARRIER,
    HARVESTER,
    BUILDER,
    UPGRADER,
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
