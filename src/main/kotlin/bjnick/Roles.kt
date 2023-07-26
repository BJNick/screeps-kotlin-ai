package bjnick

import assignedRoom
import assignedSource
import collecting
import distributionCategory
import homeRoom
import lastRoom
import lastTripDuration
import lastTripStarted
import prospectedCount
import prospectingRooms
import prospectingTargets
import role
import screeps.api.*
import settlementRoom

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
    val OUTER_HARVESTER = "OUTER_HARVESTER"
    val FREIGHTER = "CARAVAN"
}

fun Creep.executeRole() {
    if (this.spawning) {
        room.visual.text(memory.role.lowercase().capitalize(), pos.x+0.0, pos.y-1.0, options { color = "#FFFFFF"; font = "0.5"; opacity = 0.75 })
        return
    }
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
        Role.OUTER_HARVESTER -> outer_harvester()
        Role.FREIGHTER -> freighter()
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
    Role.OUTER_HARVESTER -> "#6666FF"
    Role.FREIGHTER -> "#FFFF00"
    else -> "#FFFFFF"
}



fun Creep.carrier() {
    if (useDistributionSystem(room)) // DEBUG
        room.visual.text("${intToCat(memory.distributionCategory)}"
            .take(1), pos.x+0.0, pos.y+1.0, options { color = "#66FF66"; font = "0.5"; opacity = 0.5 })

    if (!stackToCarriers())
    if (isCollecting()) {
        val dropped = findDroppedEnergy(room)
        if (dropped != null) {
            collectFrom(dropped)
            return
        }
        val target = if (useDistributionSystem(room)) {
            findConvenientEnergy(room, pickupLocationBias())
        } else {
            findConvenientEnergy()
        }
        if (target != null) {
            collectFrom(target)
        } else if (store.getUsedCapacity() > 0) {
            memory.collecting = false // keep distributing even if not full
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
        if (store.getFreeCapacity() > 0)
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
    if (room.energyAvailable < room.energyCapacityAvailable &&
        room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER } } )
            .sumOf { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY] ?: 0 } < 500) {
        // Wait until more energy is available
        if (store.getUsedCapacity() > 0)
            putEnergy(getVacantSpawnOrExt(room))
        return
    }

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
            findContainerRepairTarget() ?:
            findCloseRepairTarget(5000) ?:
            findRepairTarget(5000) ?:
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
    if (stepAwayFromBorder()) return
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
        if (gotoAssignedRoom()) return

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
        if (gotoHomeRoom()) return

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

fun Creep.settler() {
    if (stepAwayFromBorder()) return
    if (!loadAssignedRoomAndHome()) return

    if (isCollecting()) {
        if (gotoAssignedRoom()) return

        val dropped = findDroppedEnergy(room)
        if (dropped != null) {
            collectFrom(dropped)
            return
        }

        if (findFullestContainer() != null) {
            if (memory.assignedSource == "")
                unassignSource(name)
            collectFrom(findConvenientEnergy()) // TODO Bias
        } else {
            collectFromASource()
        }
    } else {
        //if (!ProgressState.carriersPresent)
        if (room.controller!!.ticksToDowngrade < 9000)
            putEnergy(room.controller)
        else {
            val containerRepair = findContainerRepairTarget()
            if (containerRepair != null) {
                goRepair(containerRepair)
                return
            }
            val target = findWorkEnergyTarget(room)
            //console.log("$name, target: ${target} pos: ${target?.pos}")
            if (target == room.controller || room.name == memory.homeRoom) {
                // REVERT TO PROSPECTOR BEHAVIOR
                if (gotoHomeRoom()) return
                putEnergy(findConvenientContainer())
            } else {
                if (target != null) {
                    //say(jsTypeOf(target).take(3)+":${target.pos.x},${target.pos.y}")
                }
                putEnergy(target)
            }
        }
    }
}

fun Creep.outer_harvester() {
    if (stepAwayFromBorder()) return
    if (!loadAssignedRoomAndHome()) return

    // For settling in nearby rooms
    if (isCollecting() && gotoAssignedRoom()) return

    if (store.getUsedCapacity(RESOURCE_ENERGY) > 0 && room.controller!!.ticksToDowngrade < 9500 && pos.getRangeTo(room.controller!!) < 5) {
        putEnergy(room.controller)
        return
    }

    // Then do basic harvester stuff
    harvester()
}

fun Creep.freighter() {
    if (!loadAssignedRoomAndHome()) return
    if (atBorder()) {
        if (memory.lastRoom == memory.assignedRoom && room.name == memory.homeRoom ||
            room.name == memory.assignedRoom && memory.lastRoom == memory.homeRoom)
            recordImportExport(memory.lastRoom, room.name, store.getUsedCapacity())
    }
    if (nearBorder()) {
        memory.lastRoom = room.name
    }

    if (stepAwayFromBorder()) return

    // Collects only in the assigned room
    // Distributes only in the home room
    if (isCollecting()) {
        if (gotoAssignedRoom()) return
        if (room.getTotalContainerEnergy() < this.store.getCapacity()!!*2) {
            // Wait for it to fill up, close to the source
            say("Waiting")
            moveIfNotInRange(findConvenientEnergy() ?: return, ERR_NOT_IN_RANGE)
            return
        }
        collectFrom(findConvenientEnergy())
    } else {
        if (gotoHomeRoom()) return
        putEnergy(findConvenientContainer())
    }

}
