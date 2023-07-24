package bjnick

import assignedRoom
import assignedSource
import collecting
import homeRoom
import prospectedCount
import prospectingRooms
import prospectingTargets
import role
import screeps.api.*

object Role {
    val UNASSIGNED = "UNASSIGNED"
    val SETTLER = "SETTLER"
    val CARRIER = "CARRIER"
    val HARVESTER = "HARVESTER"
    val BUILDER = "BUILDER"
    val UPGRADER = "UPGRADER"
    val REPAIRER = "REPAIRER"
    val PROSPECTOR = "PROSPECTOR"
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
        Role.REPAIRER -> repairer()
        Role.PROSPECTOR -> prospector()
    }
}

fun Creep.pathColor() = when (memory.role) {
    Role.UNASSIGNED -> "#FFFFFF"
    Role.SETTLER -> "#FFFFFF"
    Role.CARRIER -> "#00FF00"
    Role.HARVESTER -> "#0000FF"
    Role.BUILDER -> "#FF0000"
    Role.UPGRADER -> "#FF00FF"
    Role.REPAIRER -> "#00FFFF"
    Role.PROSPECTOR -> "#FFAA00"
    else -> "#000000"
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
    val container = findBufferContainer()
    if (isCollecting() || ProgressState.carriersPresent) { // ??? present check
        collectFromASource()
        if (container != null)
            this.transfer(container, RESOURCE_ENERGY)
    } else {
        if (container != null)
            putEnergy(container)
        else if (ProgressState.carriersPresent && !memory.collecting && store[RESOURCE_ENERGY] < store.getCapacity())
            memory.collecting = true // keep collecting even if not fully emptied
        else if (!ProgressState.carriersPresent)
            putEnergy(findEnergyTarget(room))
    }
}

fun Creep.builder() {
    if (isCollecting()) {
        //if (!ProgressState.carriersPresent)
        collectFromClosest() // TODO
    } else {
        val site = getConstructionSite(room)

        if (site != null)
            putEnergy(site)
        else {
            var toRepair = findCloseRepairTarget(5000)
            if (toRepair != null) {
                goRepair(toRepair)
                return
            }
            toRepair = findRepairTarget(5000)
            if (toRepair != null) {
                goRepair(toRepair)
                return
            }
            putEnergy(findEnergyTarget(room))
        }
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

fun Creep.repairer() {
    if (isCollecting()) {
        //if (!ProgressState.carriersPresent
        collectFromClosest() // TODO
    } else {
        // Repair critical entities
        val toRepairCritical = findCriticalRepairTarget()
        if (toRepairCritical != null) {
            goRepair(toRepairCritical)
            return
        }

        // Build walls that are at zero hits
        val wallToBuild = findWallConstructionSite()
        if (wallToBuild != null) {
            putEnergy(wallToBuild)
            return
        }
        // Repair entities with lowest hits first
        val toRepair = findRepairTarget(200) ?:
            findCloseRepairTarget(5000) ?:
            findRepairTarget(5000) ?:
            findContainerRepairTarget() ?:
            findRepairTarget(10000)
        if (toRepair != null) {
            goRepair(toRepair)
            return
        }
        say("No repairs")
    }
}

fun Creep.prospector() {
    if (memory.homeRoom == "") {
        memory.homeRoom = room.name
        memory.assignedSource = Memory.prospectingTargets.randomOrNull() ?: ""
        memory.assignedRoom = Memory.prospectingRooms.randomOrNull() ?: ""
    }
    if (memory.collecting && store[RESOURCE_ENERGY] == store.getCapacity())
        memory.prospectedCount++

    if (isCollecting()) {
        if (memory.assignedRoom == "")
            return
        val assignedRoom = Game.rooms[memory.assignedRoom]
        if (room != assignedRoom) {
            moveTo(RoomPosition(25, 25, memory.assignedRoom))
            return
        }
        val assignedSource = this.assignedSource()
        if (assignedSource == null) {
            say("No task")
            return
        }
        collectFrom(assignedSource)
    } else {
        if (memory.homeRoom == "")
            return
        val homeRoom = Game.rooms[memory.homeRoom]
        if (room != homeRoom) {
            moveTo(RoomPosition(25, 25, memory.homeRoom))
            return
        }
        val container = findClosestContainer()
        if (container != null)
            putEnergy(container)
        else
            putEnergy(findEnergyTarget(Game.rooms[memory.homeRoom] ?: return))
    }
}
