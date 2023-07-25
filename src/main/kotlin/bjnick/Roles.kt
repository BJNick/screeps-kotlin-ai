package bjnick

import assignedRoom
import assignedSource
import collecting
import distributionCategory
import homeRoom
import lastTripDuration
import lastTripStarted
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
    val CLAIMER = "CLAIMER"
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
        Role.CLAIMER -> claimer()
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
    else -> "#FFFFFF"
}


fun Creep.settler() {
    memory.assignedRoom = "E58N45" // TODO FOR THIS RUN ONLY
    // For settling in nearby rooms
    if (memory.assignedRoom != "") {
        val assignedRoom = Game.rooms[memory.assignedRoom]
        if (room != assignedRoom) {
            if (memory.assignedSource != "")
                unassignSource(name)
            moveTo(assignedRoom?.controller?.pos ?: return)
            return
        }
    }

    if (isCollecting()) {
        collectFromASource()
    } else {
        //if (!ProgressState.carriersPresent)
        if (room.controller!!.ticksToDowngrade < 9000)
            putEnergy(room.controller)
        else
            putEnergy(findWorkEnergyTarget(room))
    }
}


fun Creep.carrier() {
    if (useDistributionSystem(room)) // DEBUG
        room.visual.text("${intToCat(memory.distributionCategory)}"
            .take(1), pos.x+0.0, pos.y+1.0, options { color = "#66FF66"; font = "0.5"; opacity = 0.5 })

    if (!stackToCarriers())
    if (isCollecting()) {
        findDroppedEnergy(room)?.let {
            collectFrom(it); return
        }
        if (useDistributionSystem(room)) {
            collectFromHarvesters(room, pickupLocationBias())
        } else {
            collectFromHarvesters()
        }
    } else {
        if (useDistributionSystem(room)) {
            if (memory.distributionCategory == 0)
                pickDistributionCategory(this, room)
            putEnergy(findTargetByCategory())
        } else {
            putEnergy(findEnergyTarget(room))
        }
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
        if (!useDistributionSystem(room))
            collectFromClosest()
        else {
            if (store.getUsedCapacity() > 0)
                memory.collecting = false // keep upgrading even if not full
        }
    } else {
        putEnergy(room.controller)
    }
}

fun Creep.repairer() {
    if (isCollecting()) {
        //if (!ProgressState.carriersPresent
        collectFromClosest()
    } else {
        if (getTarget() != null) {
            if (needsRepair(getTarget())) {
                val success =goRepair(Game.getObjectById(getTarget()?.id))
                if (success) clearTarget()
                return
            } else {
                clearTarget()
            }
        }

        // Repair critical entities
        val toRepairCritical = findCriticalRepairTarget()
        if (toRepairCritical != null) {
            lockTarget(toRepairCritical)
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
            findRepairTarget(10000) ?:
            findRepairTarget(20000)
        if (toRepair != null) {
            lockTarget(toRepair)
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
    if (memory.collecting && store[RESOURCE_ENERGY] == store.getCapacity()) {
        memory.prospectedCount++
        memory.lastTripDuration = Game.time - memory.lastTripStarted
        memory.lastTripStarted = Game.time
    }

    if (isCollecting()) {
        if (memory.assignedRoom == "")
            return
        val assignedRoom = Game.rooms[memory.assignedRoom]
        if (room != assignedRoom) {
            moveTo(RoomPosition(25, 25, memory.assignedRoom))
            return
        }
        val assignedSource = this.assignedSource(false)
        if (assignedSource == null) {
            say("No task")
            memory.assignedSource = Memory.prospectingTargets.randomOrNull() ?: ""
            return
        }
        collectFrom(assignedSource)
    } else {
        if (room.controller!!.ticksToDowngrade < 9000) {
            putEnergy(room.controller)
            return
        }
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

fun Creep.claimer() {
    memory.collecting = false
    if (memory.assignedRoom == "")
        return
    val assignedRoom = Game.rooms[memory.assignedRoom]
    if (room != assignedRoom) {
        moveTo(RoomPosition(25, 25, memory.assignedRoom))
        return
    }
    if (room.controller == null)
        return
    if (room.controller?.my == false) {
        // Claim
        if (claimController(room.controller!!) == ERR_NOT_IN_RANGE)
            moveTo(room.controller!!.pos)
    }
}
