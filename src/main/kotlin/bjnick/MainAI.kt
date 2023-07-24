package bjnick

import forceReassignSources
import ignorePlayers
import lastTripDuration
import numberOfCreeps
import prospectedCount
import role
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.api.structures.StructureTower
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete
import visualizeRepairs
import kotlin.js.Date

class ProgressState {
    companion object Factory {
        val carriersPresent: Boolean
            get() = Game.creeps.values.count { it.memory.role == Role.CARRIER } > 0
    }
}

fun gameLoop() {
    val mainSpawn: StructureSpawn = Game.spawns.values.firstOrNull() ?: return

    defendRoom(mainSpawn.room)

    //delete memories of creeps that have passed away
    houseKeeping(Game.creeps)

    // just an example of how to use room memory
    mainSpawn.room.memory.numberOfCreeps = mainSpawn.room.find(FIND_CREEPS).count()

    //make sure we have at least some creeps
    spawnCreeps(Game.creeps.values, mainSpawn)

    for ((_, creep) in Game.creeps) {
        creep.executeRole()
        // If there is no road at the current position, build one
        /*if (creep.pos.lookFor(LOOK_STRUCTURES)?.isEmpty() != false && creep.pos.lookFor(LOOK_CONSTRUCTION_SITES)?.isEmpty() != false) {
            creep.room.createConstructionSite(creep.pos, STRUCTURE_ROAD)
        }*/
    }

    mainSpawn.room.visualizeSources()
    mainSpawn.room.visual.text("Epoch: ${newName("").substring(0,1)}${newName("").substring(1,2).repeat(2)}",
        24.5, 0.25, options { color = "#AAAAAA" })

    // Show a list of creeps
    val listX = 37.0
    val listY = 34.0
    //mainSpawn.room.showCreepList(Role.HARVESTER, listX, listY+0.0)
    mainSpawn.room.showCreepList(Role.BUILDER, listX, listY+1.0)
    mainSpawn.room.showCreepList(Role.CARRIER, listX, listY+2.0)
    mainSpawn.room.showCreepList(Role.UPGRADER, listX, listY+3.0)
    mainSpawn.room.showCreepList(Role.REPAIRER, listX, listY+4.0)

    // Show prospector info
    val prospectorX = 14.0
    val prospectorY = 8.0
    mainSpawn.room.showProspectorInfo(Game.creeps.values.firstOrNull { it.memory.role == Role.PROSPECTOR },
        prospectorX, prospectorY)

    if (Memory.forceReassignSources) {
        for ((creepName, _) in Memory.creeps) {
            unassignSource(creepName)
        }
        Memory.forceReassignSources = false
    }

    // When controller reaches lvl 3, place a tower construction site at the flag "TowerSpot"
    if (mainSpawn.room.controller?.level == 3) {
        val towerSpot = mainSpawn.room.find(FIND_FLAGS).firstOrNull { it.name == "TowerSpot" }
        if (towerSpot != null) {
            mainSpawn.room.createConstructionSite(towerSpot.pos, STRUCTURE_TOWER)
            towerSpot.remove()
            mainSpawn.room.placeExtensionSites(5) // only do once
        }
    }

    if (Memory.visualizeRepairs)
        mainSpawn.room.visualizeRepairs(4000)

    recordGraph(mainSpawn.room, 15, 15.0, 48.0)
}

fun Room.showCreepList(role: String, x: Double, y: Double) {
    val builders = Game.creeps.values.filter { it.memory.role == role }
    if (builders.isNotEmpty()) {
        val builderList = builders.joinToString(", ") { it.name }
        this.visual.text("${role.lowercase().capitalize()}s: $builderList", x + 0.5, y + .25,
            options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
    }
}

fun Room.showProspectorInfo(creep: Creep?, x: Double, y: Double) {
    if (creep == null) {
        this.visual.text("No prospector", x, y + 0.25,
            options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
        return
    }
    // Say the prospector name, inventory content, and distance from spawn
    val prospectorName = creep.name
    val prospectorInventory = "${creep.store.getUsedCapacity()}/${creep.store.getCapacity()}"
    val prospectorDistance = 80 - creep.pos.x - if (creep.room == this) 50 else 0//creep.pos.getRangeTo(this.find(FIND_MY_SPAWNS)[0])
    this.visual.text("Prospector $prospectorName", x, y + 0.25,
        options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
    this.visual.text("Inventory: $prospectorInventory", x, y + 1.25,
        options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
    this.visual.text("Distance: $prospectorDistance", x, y + 2.25,
        options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
    this.visual.text("Done: ${creep.memory.prospectedCount}", x, y + 3.25,
        options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
    // show last trip duration
    val lastTripDuration = creep.memory.lastTripDuration
    this.visual.text("Duration: $lastTripDuration", x, y + 4.25,
        options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT })
}

private fun houseKeeping(creeps: Record<String, Creep>) {
    if (Game.creeps.isEmpty()) return  // this is needed because Memory.creeps is undefined

    for ((creepName, _) in Memory.creeps) {
        if (creeps[creepName] == null) {
            console.log("deleting obsolete memory entry for creep $creepName")
            unassignSource(creepName)

            // If prospector, send report
            if (Memory.creeps[creepName]?.role == Role.PROSPECTOR) {
                Game.notify("${getSKTime()} Prospector $creepName died, made ${Memory.creeps[creepName]?.prospectedCount} trips")
            }

            delete(Memory.creeps[creepName])
        }
    }
}

fun getSKTime(): String {
    return Date(Date.now() - 6 * 3600000).toLocaleString("en-CA")
}

fun defendRoom(room: Room) {
    val hostiles = room.find(FIND_HOSTILE_CREEPS)
    val damagedCreeps = room.find(FIND_MY_CREEPS, options { filter = { it.hitsMax - it.hits > 0 } })
    val towers = room.find(FIND_MY_STRUCTURES, options { filter = { it.structureType == STRUCTURE_TOWER } }) as Array<StructureTower>
    if (hostiles.isNotEmpty()) {
        val username = hostiles[0].owner.username
        if (username != "Invader" && !(Memory.ignorePlayers.contains(username)))
            Game.notify("${getSKTime()} $username spotted in room ${room.name}")
        if (!(Memory.ignorePlayers.contains(username)))
            towers.forEach { tower -> tower.attack(hostiles[0]) }
        // ACTIVATE SAFE MODE
        if (room.controller?.safeModeAvailable > 0 && room.controller?.safeModeCooldown == 0)
            room.controller?.activateSafeMode()
    } else if (damagedCreeps.isNotEmpty()) {
        towers.forEach { tower -> tower.heal(damagedCreeps[0]) }
    } else {
        towers.forEach {
            val repairTarget = it.findTowerRepairTarget(500) ?: it.findTowerRepairTarget(5000)
            if (repairTarget != null)
                it.repair(repairTarget)
        }
    }
}

fun Room.visualizeRepairs(showUnder: Int) {
    val toBeRepaired = this.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < showUnder } })
    toBeRepaired.forEach { this.visual.circle(it.pos, options { radius = 0.5; fill = "#ff0000"; opacity = 0.2 }) }
}

