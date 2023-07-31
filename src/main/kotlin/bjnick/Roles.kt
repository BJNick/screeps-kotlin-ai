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
import prospectorsUpgradeController
import role
import screeps.api.*
import screeps.api.structures.*

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
    val ERRANDER = "ERRANDER"
    val BOUNCER = "BOUNCER"
    val EXTRACTOR = "EXTRACTOR"
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
        Role.ERRANDER -> errander()
        Role.BOUNCER -> bouncer()
        Role.EXTRACTOR -> extractor()
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
    Role.ERRANDER -> "#AAFF00"
    Role.BOUNCER -> "#FF0000"
    Role.EXTRACTOR -> "#FFFFFF"
    else -> "#FFFFFF"
}



fun Creep.carrier() {
    if (stepAwayFromBorder()) return
    if (gotoGlobalHomeRoom()) return // Home room only role

    if (useDistributionSystem(room))
        if (memory.distributionCategory == 0) // FORCE ASSIGNMENT
            pickDistributionCategory(this, room)
    if (useDistributionSystem(room)) // DEBUG
        room.visual.text("${intToCat(memory.distributionCategory)}"
            .take(2), pos.x+0.0, pos.y+1.0, options { color = "#66FF66"; font = "0.5"; opacity = 0.5 })

    if (executeCachedTask()) return // TODO TESTING

    if (stackToCarriers()) return

    if (isCollecting()) {
        val dropped = findDroppedEnergy(room)
        if (dropped != null) {
            collectFrom(dropped)
            return
        }

        // Mineral container that accidentally has energy in it
        val mineralContainer = room.byOrder(STRUCTURE_CONTAINER, f = hasAtLeastEnergy(1) and hasTag(MINERAL_BUFFER))
        if (mineralContainer != null) {
            collectFrom(mineralContainer)
            return
        }

        /*val target = if (useDistributionSystem(room)) {
            findConvenientEnergy(room, pickupLocationBias())
        } else {*/
        // ALWAYS PICK THE CLOSEST
        // TODO Encapsulate distribution behaviour
        if (memory.distributionCategory == catToInt(DistributionCategory.STORAGE)) {
            val target = room.byOrder(STRUCTURE_CONTAINER, f = hasAtLeastEnergy(1950) )
            if (target != null) {
                collectFrom(target)
                return
            }
        }

        val target = findConvenientEnergy()
        //}
        if (target != null) {
            collectFrom(target)
        } else if (store.getUsedCapacity() > 0) {
            memory.collecting = false // keep distributing even if not full
        }
    } else {

        if (room.energyAvailable < room.energyCapacityAvailable/2) {
            if (Game.time % 10 == name.hashCode()%10)
                say("Spawn!")
            putEnergy(getVacantSpawnOrExt(room)) // EMERGENCY MEASURES

        } else if (useDistributionSystem(room))
            putEnergy(findTargetByCategory())
        else
            putEnergy(findEnergyTarget(room))
        // TODO: MILITARY MEASURES ONLY
        /*if (useDistributionSystem(room) && Game.creeps.values.count { it.memory.role == Role.RANGER } >= 1) {
            if (memory.distributionCategory == 0)
                pickDistributionCategory(this, room) // WHAT DOES THIS DO??
            putEnergy(findTargetByCategory())
        } else {
            putEnergy(findEnergyTarget(room))
        }*/
    }
}

fun Creep.harvester(toHomeRoom: Boolean = true) {
    if (stepAwayFromBorder()) return
    if (toHomeRoom && gotoGlobalHomeRoom()) return // Home room only role

    val container = findBufferContainer()
    if (isCollecting()) {
        if (store.getFreeCapacity() > 0)
            collectFromASource()
        if (container != null && container.hits >= 35000 && store.getUsedCapacity() >= 40 &&
                container.store.getFreeCapacity(RESOURCE_ENERGY) > 0) {
            this.transfer(container, RESOURCE_ENERGY)
        }
    } else {
        if (container != null && container.store.getFreeCapacity(RESOURCE_ENERGY) > 0) {
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
    if (stepAwayFromBorder()) return
    if (gotoGlobalHomeRoom()) return // Home room only role

    if (executeCachedTask()) return // TODO TESTING

    /*if (room.energyAvailable < room.energyCapacityAvailable &&
        room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER } } )
            .sumOf { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY] ?: 0 } < 500) {
        // Wait until more energy is available
        if (store.getUsedCapacity() > 0)
            putEnergy(getVacantSpawnOrExt(room))
        return
    }*/

    if (isCollecting()) {
        //if (!ProgressState.carriersPresent)
        collectFrom(findConvenientEnergy())
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
    if (stepAwayFromBorder()) return
    if (gotoGlobalHomeRoom()) return // Home room only role

    val container = findBufferContainer()
    if (isCollecting()) {
        if (container != null && store.getUsedCapacity() < 10 && container.store[RESOURCE_ENERGY] > 0) {
            if (container.pos.inRangeTo(pos, 1)) {
                this.withdraw(container, RESOURCE_ENERGY)
            } else {
                collectFrom(container)
                return
            }
        } else if (!useDistributionSystem(room))
            collectFrom(findConvenientEnergy())
        else {
            if (store.getUsedCapacity() > 0)
                memory.collecting = false // keep upgrading even if not full
        }
    } else {
        if (container != null && store.getUsedCapacity() < 10 && container.store[RESOURCE_ENERGY] > 0) {
            if (container.pos.inRangeTo(pos, 1)) {
                this.withdraw(container, RESOURCE_ENERGY)
            } else {
                collectFrom(container)
                return
            }
        }
        putEnergy(room.controller)
    }
}

fun Creep.repairer() {
    if (stepAwayFromBorder()) return
    if (gotoGlobalHomeRoom()) return // Home room only role

    if (executeCachedTask()) return // TODO TESTING

    if (isCollecting()) {
        //if (!ProgressState.carriersPresent
        collectFrom(findConvenientEnergy())
    } else {
        // OBSOLETE target locking
        /*if (getTarget() != null) {
            if (needsRepair(getTarget())) {
                val success =goRepair(Game.getObjectById(getTarget()?.id))
                if (success) clearTarget()
                return
            } else {
                clearTarget()
            }
        }*/

        // Repair critical entities
        val toRepairCritical = findCriticalRepairTarget()
        if (toRepairCritical != null) {
            //lockTarget(toRepairCritical)
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
            //lockTarget(toRepair)
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

        val dropped = findDroppedEnergy(room)
        if (dropped != null) {
            collectFrom(dropped)
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
        if (room.controller!!.ticksToDowngrade < 9000 || room.controller!!.level == 1) {
            // And also its the closest creep to the controller
            val closestCreep = room.bySort(FIND_CREEPS, f = hasRole(Role.PROSPECTOR),
                sort = byDistance(room.controller!!.pos) then byTrueFirst { it.name == name })
            if (closestCreep == this) {
                putEnergy(room.controller)
                return
            }
        }
        // Try to build construction sites in the assigned room
        if (room.name == memory.assignedRoom) {
            // TODO replace them with SETTLERS
            val containerRepair = findContainerRepairTarget()
            if (containerRepair != null) {
                goRepair(containerRepair)
                return
            }

            val site = getConstructionSite(room)
            if (site != null) {
                putEnergy(site)
                return
            }
            // OR repair structures in the assigned room
            val toRepair: Structure? = findMilitaryRepairTarget(5000,  bias = pos) ?:
            findInfrastructureRepairTarget(5000, bias = pos) ?:
            findMilitaryRepairTarget(10000,  bias = pos)
            if (toRepair != null) {
                room.visual.circle(toRepair.pos, options { fill = "#00FFAA"; opacity=0.3; radius = 0.3 })
                goRepair(toRepair)
                return
            }
        }

        if (room.name == memory.assignedRoom && room.memory.prospectorsUpgradeController) {
            putEnergy(room.controller)
            return
        }


        if (memory.homeRoom == "")
            return
        if (gotoGlobalHomeRoom()) return

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
    //memory.preferredPathCache = 10

    if (!loadAssignedRoomAndHome()) return
    recordBorderMovements()

    if (stepAwayFromBorder()) return

    //if (executeCachedTask()) return // TODO TESTING

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

            if (store.getUsedCapacity(RESOURCE_ENERGY) > 0) {
                // Find closest reparable object and repair it on the way to other tasks
                val toRepairClosest = room.bySort(
                    FIND_STRUCTURES, f = lessThanMaxHits and lessHitsThan(5000)
                            and withinRange(3, pos), sort = byDistance(pos)
                )
                if (toRepairClosest != null) {
                    repair(toRepairClosest)
                }
            }


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

            var toRepair: Structure? = findMilitaryRepairTarget(4000,  bias = pos) ?:
                findInfrastructureRepairTarget(4000, bias = pos) ?:
                findMilitaryRepairTarget(10000, bias = cornerBias())

            if (toRepair != null) {
                room.visual.circle(toRepair.pos, options { fill = "#00FFAA"; opacity=0.3; radius = 0.3 })
                goRepair(toRepair)
                return
            }

            val target = findWorkEnergyTarget(room)
            if (target != null && target != room.controller) {
                putEnergy(target)
                return
            }

            toRepair = findRepairTarget(5000) ?:
                findMilitaryRepairTarget(20000) ?:
                findRepairTarget(10000) ?:
                findRepairTarget(20000)
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

    /*if (Game.time % 4 == name.hashCode() % 4)
        say(arrayOf(">:(", "No attac", "We mine").random(), true) // TODO: Tell other players
*/
    if (store.getUsedCapacity(RESOURCE_ENERGY) > 0 && room.controller!!.ticksToDowngrade < 9500 && pos.getRangeTo(room.controller!!) < 5) {
        putEnergy(room.controller)
        return
    }

    val source = assignedSource()
    val sourceVal = source?.energy ?: -1
    val container = findBufferContainer()
    val containerVal = container?.store?.getUsedCapacity(RESOURCE_ENERGY) ?: 0

    // SUPPLY TOWER
    val closestTower = room.bySort(STRUCTURE_TOWER, f = hasAtMostEnergy(999) and withinRange(2,pos),
        sort = byDistance(pos))?.unsafeCast<StructureTower>()
    if ((store.getUsedCapacity(RESOURCE_ENERGY) >= 40 || sourceVal == 0) && closestTower != null) {
        if (store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
            collectFrom(container)
            return
        }
        if (pos.getRangeTo(closestTower) > 1) {
            putEnergy(closestTower)
            return
        } else {
            transfer(closestTower, RESOURCE_ENERGY)
        }
    }

    // IF EMPTY, BUILD CONSTRUCTION SITES
    if (sourceVal == 0) {
        val closestConstructionSite = getConstructionSite(room)
        if (closestConstructionSite != null && closestConstructionSite.pos.getRangeTo(pos) < 10) {
            if (store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
                collectFrom(container)
                return
            }
            putEnergy(closestConstructionSite)
            return
        }
    }

    // IF EMPTY AND CONTROLLER, PUT ENERGY IN CONTROLLER
    if ((sourceVal == 0 || containerVal == 2000) && pos.getRangeTo(room.controller!!) < 5) {
        if (store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
            if (sourceVal == 0) {
                collectFrom(container)
                return
            } else {
                collectFrom(source)
                return
            }
        }
        putEnergy(room.controller)
        return
    }

    // Also if empty and NO CONTROLLER, repair things around
    if (sourceVal == 0) {
        val toRepair = room.bySort(STRUCTURE_ROAD, f = lessThanMaxHits and lessHitsThan(4500)
                and withinRange(10, pos), sort = byDistance(pos))
        if (toRepair != null) {
            if (store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
                collectFrom(container)
                return
            }
            goRepair(toRepair)
            return
        }
    } else {
        moveWithin(source!!.pos, 1)
    }

    // Then do basic harvester stuff
    harvester(false)
}

fun Creep.caravan() {
    if (!loadAssignedRoomAndHome()) return
    recordBorderMovements()

    if (stepAwayFromBorder()) return

    if (executeCachedTask()) return // TODO TESTING

    // Collects only in the assigned room
    // Distributes only in the home room
    if (isCollecting()) {
        if (gotoAssignedRoom()) return

        val dropped = findDroppedEnergy(room)
        if (dropped != null && dropped.pos.getRangeTo(this) < 5) {
            collectFrom(dropped)
            return
        }

        var destination = findCaravanPickupEnergy(bias = cornerBias())
        if (room.getTotalContainerEnergy() < this.store.getCapacity()!!*1.5) {
            // Wait for it to fill up, close to the source
            if (Game.time % 4 == name.hashCode() % 4)
                say("Waiting")
            destination = destination ?: room.byOrder(STRUCTURE_CONTAINER)
            if (destination != null)
                moveWithin(destination!!.pos, 2)
            return
        }
        collectFrom(destination)
    } else {
        if (gotoGlobalHomeRoom()) return
        putEnergy(findConvenientContainer(avoidTags = arrayOf(MINERAL_BUFFER)))
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

        val closestRampart = room.bySort(STRUCTURE_RAMPART, f = isUnoccupied(this),
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
            val wallRuinPresent = room.byOrder(FIND_RUINS, f = { it.structure == STRUCTURE_WALL }) != null

            if (onTheOtherSide && !wallRuinPresent)
                moveWithin(closestWallToEnemy.pos, 1)
            else
                moveWithin(closestHostile.pos, 3)

            // visualize
            room.visual.circle(closestWallToEnemy.pos, options { radius = 0.5; fill = "#ffAA00"; opacity = 0.5 })
        }


        // IF CLOSE ENOUGH, ATTACK!!!
        if (closestDistance <= 1) {
            // ATTACK (MASS)
            rangedMassAttack()
        } else
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


fun Creep.errander() {
    if (stepAwayFromBorder()) return
    if (gotoGlobalHomeRoom()) return // Home room only role

    room.visual.text("E", pos.x+0.0, pos.y+1.0, options { color = "#FFFFFF"; font = "0.5"; opacity = 0.5 })

    if (isCollecting()) {
        val dropped = findDroppedEnergy(room)
        if (dropped != null) {
            collectFrom(dropped)
            return
        }

        val target = findConvenientEnergy(takeFromCarriers = 2)

        if (target != null) {
            collectFrom(target)
        } else if (store.getUsedCapacity() > 0) {
            memory.collecting = false // keep distributing even if not full
        }
    } else {

        val otherErranders = room.find(FIND_MY_CREEPS, options { filter = {it.name != name && it.memory.role == Role.ERRANDER }})

        val towers = room.find(FIND_MY_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER }})
            .unsafeCast<Array<StructureTower>>()

        // REFILL TOWERS
        for (it in towers) {
            // Check tower supplier
            val other = otherErranders.bySort(sort = byDistance(it.pos))?.unsafeCast<Creep>()
            if (other == null || other.pos.getRangeTo(it.pos) > 6) {
                // No other erranders are close to this tower
                if (it.store.getFreeCapacity(RESOURCE_ENERGY) > 0) {
                    putEnergy(it)
                    return
                } else {
                    moveWithin(it.pos, 1)
                    return
                }
            }
        }

        // REFILL SPAWN
        val spawn = room.find(FIND_MY_SPAWNS).firstOrNull()

        if (spawn != null) {
            val other = otherErranders.bySort(sort = byDistance(spawn.pos))?.unsafeCast<Creep>()
            if (other == null || other.pos.getRangeTo(spawn.pos) > 7) {
                // No other erranders are close to the spawn
                val target = getVacantSpawnOrExt(room)
                if (target != null) {
                    putEnergy(target)
                    return
                } else {
                    moveWithin(spawn.pos, 3)
                    return
                }
            }
        }

        // REFILL UPGRADER CREEPS
        val upgrader = room.bySort(FIND_MY_CREEPS, f = hasRole(Role.UPGRADER), sort = byMostFree then byDistance(this.pos))

        if (upgrader != null) {
            val it = upgrader.unsafeCast<Creep>()
            val other = otherErranders.bySort(sort = byDistance(it.pos))?.unsafeCast<Creep>()
            if (other == null || other.pos.getRangeTo(it.pos) > 3) {
                // No other erranders are close to this upgrader
                val buffer = room.byOrder(STRUCTURE_CONTAINER, f = hasNoEnergy and withinRange(3, room.controller!!.pos))
                    ?.unsafeCast<StructureContainer>()
                if (buffer != null) {
                    putEnergy(buffer)
                    return
                }

                if (it.store.getFreeCapacity(RESOURCE_ENERGY) > 0) {
                    putEnergy(it)
                    return
                } else {
                    moveWithin(it.pos, 1)
                    return
                }
            }
        }


        val target = findEnergyTarget(room)
        putEnergy(target)

    }
}



fun Creep.bouncer() {
    if (!loadAssignedRoomAndHome()) return
    recordBorderMovements()

    if (stepAwayFromBorder()) return

    if (Memory.defendRoom == "") {
        say("No room!")
        return
    }

    val hostileCreeps = room.find(FIND_HOSTILE_CREEPS)

    val closestHostile: HasPosition? = room.bySort(FIND_HOSTILE_CREEPS, sort = byDistance(this.pos))?.unsafeCast<HasPosition>()
        ?: room.byOrder(FIND_FLAGS, f = { it.name == "Enemy" })?.unsafeCast<HasPosition>()

    if (closestHostile != null) {
        // HOSTILE MODE ACTIVATED

        val mostDangerous: Creep = room.bySort(FIND_HOSTILE_CREEPS,
            sort = byMostBodyParts(ATTACK, RANGED_ATTACK)).unsafeCast<Creep>()
        val healer: Creep? = room.bySort(FIND_HOSTILE_CREEPS, f = hasBodyPart(HEAL), sort = byMostBodyParts(HEAL))?.unsafeCast<Creep>()


        val closestDistance = closestHostile.pos.getRangeTo(this.pos)

        val closestRampart = room.bySort(STRUCTURE_RAMPART, f = isUnoccupied(this),
            sort = byEuclideanDistance(closestHostile.pos))?.unsafeCast<StructureRampart>()

        if (closestRampart != null && closestRampart.pos.getRangeTo(closestHostile.pos) <= 1) {
            // HIDE WITHIN RAMPART
            moveWithin(closestRampart.pos, 0)
        } else if (closestDistance > 1) {
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
                moveWithin(closestHostile.pos, 1)

            // visualize
            room.visual.circle(closestWallToEnemy.pos, options { radius = 0.4; fill = "#ffAA00"; opacity = 0.5 })
        }

        // IF CLOSE ENOUGH, ATTACK!!!
        if (closestDistance <= 1) {
            val toAttack = room.bySort(FIND_HOSTILE_CREEPS, f = withinRange(1, this.pos),
                sort = byMostBodyParts(ATTACK, RANGED_ATTACK) then byHits).unsafeCast<Creep>()
            // ATTACK (MELEE)
            attack(toAttack)
        }

    } else {
        // PEACEFUL MODE
        if (moveToRoom(Memory.defendRoom)) return

        val outpostFlag = Game.flags["BouncerOutpost"]
        if (outpostFlag != null) {
            moveWithin(outpostFlag.pos)
        }
    }


}

fun Creep.extractor() {
    if (stepAwayFromBorder()) return
    if (gotoGlobalHomeRoom()) return // Home room only role

    // Extracts minerals from a mineral deposit
    // does not move to other rooms
    val container = findBufferContainer()
    if (isCollecting()) {
        if (store.getFreeCapacity() > 0) {
            collectFromMineral()
        }
        if (container != null && mineralInStore()!=null && store.getUsedCapacity() >= 40 &&
            container.store.getFreeCapacity() > 0) {
            this.transfer(container, mineralInStore()!!)
        }
    } else {
        if (mineralInStore() == null) {
            console.log("Creep $name has no mineral in store to put in container")
            return
        }
        if (container != null && container.store.getFreeCapacity() > 0) {
            putResource(container, mineralInStore()!!)
        } else {
            // Last resort: put in storage
            if (room.storage != null) {
                putResource(room.storage!!, mineralInStore()!!)
            }
        }
    }
}