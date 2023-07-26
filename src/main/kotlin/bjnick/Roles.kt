package bjnick

import assignedRoom
import assignedSource
import collecting
import defendRoom
import distributionCategory
import homeRoom
import lastTripDuration
import lastTripStarted
import prospectedCount
import prospectingRooms
import prospectingTargets
import role
import screeps.api.*
import screeps.api.structures.StructureRampart
import screeps.api.structures.StructureWall

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
    val CARAVAN = "CARAVAN"
    val RANGER = "RANGER"
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
        Role.CARAVAN -> caravan()
        Role.RANGER -> ranger()
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
    Role.CARAVAN -> "#FFFF00"
    Role.RANGER -> "#FF00FF"
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
        // TODO: MILITARY MEASURES ONLY
        if (useDistributionSystem(room) && Game.creeps.values.count { it.memory.role == Role.RANGER } >= 1) {
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
    if (isCollecting()) {
        if (store.getFreeCapacity() > 0)
            collectFromASource()
        if (container != null && container.hits >= 35000) {
            this.transfer(container, RESOURCE_ENERGY)
        }
    } else {
        if (container != null) {
            if (container.hits < 35000) {
                goRepair(container)
            } else {
                putEnergy(container)
            }
        } else if (ProgressState.carriersPresent && !memory.collecting && store[RESOURCE_ENERGY] < store.getCapacity())
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

    recordBorderMovements()
    if (stepAwayFromBorder()) return

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
    if (!loadAssignedRoomAndHome()) return
    recordBorderMovements()

    if (stepAwayFromBorder()) return

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
            val wallSites = findWallConstructionSite()
            if (wallSites != null) {
                putEnergy(wallSites)
                return
            }

            val containerRepair = findContainerRepairTarget()
            if (containerRepair != null) {
                goRepair(containerRepair)
                return
            }

            val milRepair = findMilitaryRepairTarget(bias = cornerBias())
            if (milRepair != null) {
                goRepair(milRepair)
                return
            }

            val target = findWorkEnergyTarget(room)
            if (target != null && target != room.controller) {
                putEnergy(target)
            }

            val toRepair = findRepairTarget(5000) ?: findRepairTarget(10000)
            if (toRepair != null) {
                goRepair(toRepair)
                return
            }

            putEnergy(room.controller)

            //console.log("$name, target: ${target} pos: ${target?.pos}")
            // NEED TO UPGRADE CONTROLLER
            /*if (target == room.controller || room.name == memory.homeRoom) {
                // REVERT TO PROSPECTOR BEHAVIOR
                if (gotoHomeRoom()) return
                putEnergy(findConvenientContainer())
            } else {
                if (target != null) {
                    //say(jsTypeOf(target).take(3)+":${target.pos.x},${target.pos.y}")
                }
            }*/
        }
    }
}

fun Creep.outer_harvester() {
    if (stepAwayFromBorder()) return
    if (!loadAssignedRoomAndHome()) return

    // For settling in nearby rooms
    if (isCollecting() && gotoAssignedRoom()) return

    if (Game.time % 4 == name.hashCode() % 4)
        say(arrayOf("Want peace", "No attac", "We mine").random()) // TODO: Tell other players

    if (store.getUsedCapacity(RESOURCE_ENERGY) > 0 && room.controller!!.ticksToDowngrade < 9500 && pos.getRangeTo(room.controller!!) < 5) {
        putEnergy(room.controller)
        return
    }

    val source = assignedSource()
    val container = findBufferContainer()
    if ((source?.energy == 0 || container?.store?.getFreeCapacity(RESOURCE_ENERGY) == 0) && pos.getRangeTo(room.controller!!) < 5) {
        if (store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
            collectFrom(container)
            return
        }
        putEnergy(room.controller)
        return
    }

    // Then do basic harvester stuff
    harvester()
}

fun Creep.caravan() {
    if (!loadAssignedRoomAndHome()) return
    recordBorderMovements()

    if (stepAwayFromBorder()) return

    // Collects only in the assigned room
    // Distributes only in the home room
    if (isCollecting()) {
        if (gotoAssignedRoom()) return
        if (room.getTotalContainerEnergy() < this.store.getCapacity()!!*2) {
            // Wait for it to fill up, close to the source
            say("Waiting")
            moveIfNotInRange(findCaravanPickupEnergy() ?: return, ERR_NOT_IN_RANGE)
            return
        }
        collectFrom(findCaravanPickupEnergy())
    } else {
        if (gotoHomeRoom()) return
        putEnergy(findConvenientContainer())
    }
}

fun Creep.ranger() {
    if (!loadAssignedRoomAndHome()) return
    recordBorderMovements()

    if (Memory.defendRoom == "") {
        say("No room!")
        return
    }

    if (moveToRoom(Memory.defendRoom)) return


    val hostileCreeps = room.find(FIND_HOSTILE_CREEPS)
    val closestHostile: HasPosition? = room.bySort(FIND_HOSTILE_CREEPS, sort = byDistance(this.pos))?.unsafeCast<HasPosition>()
        ?: room.byOrder(FIND_FLAGS, f = { it.name == "Enemy" })?.unsafeCast<HasPosition>()

    if (closestHostile != null) {
        // HOSTILE MODE ACTIVATED

        val mostDangerous: Creep = room.bySort(FIND_HOSTILE_CREEPS,
            sort = byMostBodyParts(ATTACK, RANGED_ATTACK)).unsafeCast<Creep>()
        val healer: Creep? = room.bySort(FIND_HOSTILE_CREEPS, f = hasBodyPart(HEAL), sort = byMostBodyParts(HEAL))?.unsafeCast<Creep>()

        if (hostileCreeps.isNotEmpty())
            console.log("Hostile creep spotted in room ${room.name}! ${mostDangerous.name ?: "NONE"} " +
                    "is the most dangerous, and ${healer?.name ?: "NONE"} is the healer.")

        val closestDistance = closestHostile.pos.getRangeTo(this.pos)

        val closestRampart = room.bySort(STRUCTURE_RAMPART,
            sort = byEuclideanDistance(closestHostile.pos))?.unsafeCast<StructureRampart>()

        if (closestRampart != null && closestRampart.pos.getRangeTo(closestHostile.pos) <= 3) {
            // HIDE WITHIN RAMPART
            moveWithin(closestRampart.pos, 0)
        } else if (closestDistance <= 2) {
            // RUN AWAY IN OPPOSITE DIRECTION
            val hPos = closestHostile.pos
            // Pick an anchor point the furthest away from the hostile
            val oppositePos = RoomPosition(bindCoordinate(pos.x + 2*(pos.x - hPos.x)), bindCoordinate(pos.y + 2*(pos.y - hPos.y)), room.name)
            val oppositeAnchor = room.bySort(arrayOf(STRUCTURE_CONTROLLER, STRUCTURE_CONTAINER), f = onOppositeSideOf(pos, hPos),
                sort = byEuclideanDistance(hPos).reversed())?.pos
            if (room.getTerrain().get(oppositePos.x, oppositePos.y) == TERRAIN_MASK_WALL && oppositeAnchor != null) {
                room.visual.circle(oppositeAnchor, options { radius = 0.5; fill = "#00AAff"; opacity = 0.5 })
                moveWithin(oppositeAnchor, 1)
            } else {
                room.visual.circle(oppositePos, options { radius = 0.5; fill = "#00AAff"; opacity = 0.5 })
                moveWithin(oppositePos, 0)
            }
        } else if (closestDistance > 3) {
            // MOVE TOWARDS WALL THAT IS CLOSEST TO ENEMY
            val closestWallToEnemy = room.bySort(
                STRUCTURE_WALL,
                sort = byEuclideanDistance(closestHostile.pos)
            ).unsafeCast<StructureWall>()

            // MOVE TOWARDS WALL IF ENEMY IS ON OPPOSITE SIDE
            // IF ENEMY ON SAME SIDE, MOVE TOWARDS ENEMY
            val onTheOtherSide = !onOppositeSideOf(this.pos, closestWallToEnemy.pos)(closestHostile)

            if (onTheOtherSide)
                moveWithin(closestWallToEnemy.pos, 1)
            else
                moveWithin(closestHostile.pos, 3)

            // visualize
            room.visual.circle(closestWallToEnemy.pos, options { radius = 0.5; fill = "#ffAA00"; opacity = 0.5 })
        }

        // IF CLOSE ENOUGH, SHOOT!!!
        if (closestDistance <= 3) {
            val toAttack = room.bySort(FIND_HOSTILE_CREEPS,
                sort = byDistance(this.pos) then byMostBodyParts(ATTACK, RANGED_ATTACK) then byHits).unsafeCast<Creep>()
            // ATTACK (RANGER)
            rangedAttack(toAttack)
        }

    } else {
        // PEACEFUL MODE
        val outpostFlag = Game.flags["RangerOutpost"]
        if (outpostFlag != null) {
            moveWithin(outpostFlag.pos)
        }
    }


}

