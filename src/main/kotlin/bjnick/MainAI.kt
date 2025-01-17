package bjnick

import enableVisualizations
import forceReassignDistributionCategories
import forceReassignSources
import graphOnTopSide
import ignorePlayers
import lastTripDuration
import org.w3c.dom.Text
import outputCPUUsage
import outputCreepCPUUsage
import outputStructureCache
import prospectedCount
import recordData
import role
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.api.structures.StructureTower
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete
import visualizeCPUUsage
import visualizeDyingCreeps
import visualizeGraphs
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
    var cpuBegin = Game.cpu.getUsed()

    if (Memory.outputCPUUsage) {
        console.log("///////////////////////////")
        console.log("Parsing CPU: ${Game.cpu.getUsed() - cpuBegin}")
    }
    cpuBegin = Game.cpu.getUsed()

    val cpuInitial = Game.cpu.getUsed()

    Game.rooms.values.forEach {
        defendRoom(it)
    }
    
    // Defence CPU
    if (Memory.outputCPUUsage) {
        console.log("Defence CPU: ${Game.cpu.getUsed() - cpuBegin}")
    }
    cpuBegin = Game.cpu.getUsed()

    //delete memories of creeps that have passed away
    houseKeeping(Game.creeps)

    // Housekeeping CPU
    if (Memory.outputCPUUsage) {
        console.log("Housekeeping CPU: ${Game.cpu.getUsed() - cpuBegin}")
    }
    cpuBegin = Game.cpu.getUsed()

    // just an example of how to use room memory
    //mainSpawn.room.memory.numberOfCreeps = mainSpawn.room.find(FIND_CREEPS).count()

    //console.log(mainSpawn.room.energyAvailable)

    //make sure we have at least some creeps
    spawnCreeps(Game.creeps.values, mainSpawn)

    if (Memory.outputCPUUsage) {
        console.log("Spawn CPU: ${Game.cpu.getUsed() - cpuBegin}")
    }
    cpuBegin = Game.cpu.getUsed()

    // TODO DOES NOT WORK
    /*if (mainSpawn.spawning != null) {
        // SECONDARY SPAWN
        val spawn = Game.spawns.values.firstOrNull { it.spawning == null }
        if (spawn != null)
            spawnCreeps(Game.creeps.values, spawn)
    }*/

    val cpuUsage: MutableList<String> = mutableListOf()

    for ((_, creep) in Game.creeps) {
        val before = Game.cpu.getUsed()
        try {
            creep.executeRole()
        } catch (e: dynamic) {
            console.log("Error in creep " + creep?.name + " with role " + creep?.memory?.role + ": " + e?.message + "\n" + e?.stack)
            continue
        }
        // MEASURE PERFORMANCE
        if (Memory.visualizeCPUUsage || Memory.outputCreepCPUUsage) {
            val after = Game.cpu.getUsed()
            cpuUsage.add("${after - before} is the usage of |${creep.name}| with role ${creep.memory.role}")
        }
    }

    // Show total Creep CPU usage
    if (Memory.outputCPUUsage) {
        console.log("Creep CPU: ${Game.cpu.getUsed() - cpuBegin}")
    }
    cpuBegin = Game.cpu.getUsed()

    // Show CPU usage
    if (Memory.outputCreepCPUUsage) {
        cpuUsage.sort()
        cpuUsage.takeLast(10).forEach { console.log(it) }
    }
    // Or Visualize CPU usage on each creep
    if (Memory.visualizeCPUUsage) {
        for (s in cpuUsage) {
            val split = s.split("|")
            val creepName = split[1]
            val creep = Game.creeps[creepName]
            val colorC = if (s.take(1) == "0") "#FFFFFF" else "#FFAAAA"
            creep?.room?.visual?.text(split[0].take(3), creep.pos.x+0.0, creep.pos.y+0.0, options { color = colorC })
        }
    }

    // Start post processing
    cpuBegin = Game.cpu.getUsed()

    if (Memory.forceReassignSources) {
        for ((creepName, _) in Memory.creeps) {
            unassignSource(creepName)
        }
        Memory.forceReassignSources = false
    }

    if (Memory.forceReassignDistributionCategories) {
        for ((creepName, _) in Memory.creeps) {
            unassignDistribution(creepName, mainSpawn.room) // TODO: generalize
        }
        Memory.forceReassignDistributionCategories = false
    }

    // Assign tags to containers
    Game.rooms.values.forEach {
        it.tagContainers()
    }

    // If there is a ruin of a rampart/wall put a construction site on it
    Game.rooms.values.forEach {
        it.find(FIND_RUINS)
            .forEach { ruin ->
            if (ruin.structure.structureType == STRUCTURE_RAMPART || ruin.structure.structureType == STRUCTURE_WALL) {
                if (ruin.pos.lookFor(LOOK_CONSTRUCTION_SITES)?.isEmpty() != false)
                    ruin.pos.createConstructionSite(ruin.structure.structureType.unsafeCast<BuildableStructureConstant>())
            }
        }
    }


    val saveCPU = Game.cpu.getUsed() > Game.cpu.limit

    if (Memory.enableVisualizations && !saveCPU) {

        Game.rooms.values.forEach {
            it.visualizeSources()
            // If it has a storage, show the storage content
            if (it.storage != null) {
                val storage = it.storage!!
                val storageContent = "${storage.store.getUsedCapacity(RESOURCE_ENERGY)}"
                it.visual.text(storageContent, storage.pos.x + 0.0, storage.pos.y + 2 + 0.25,
                    options { color = "#FFFFAA"; align = TEXT_ALIGN_CENTER })
            }
        }

        mainSpawn.room.visual.text("Epoch: ${newName("").substring(0, 1)}${newName("").substring(1, 2).repeat(2)}",
            24.5, 0.25, options { color = "#AAAAAA" })

        // Show a list of creeps
        val listX = 37.0
        val listY = 34.0
        //mainSpawn.room.showCreepList(Role.HARVESTER, listX, listY+0.0)
        mainSpawn.room.showCreepList(Role.BUILDER, listX, listY + 1.0)
        mainSpawn.room.showCreepList(Role.CARRIER, listX, listY + 2.0)
        mainSpawn.room.showCreepList(Role.UPGRADER, listX, listY + 3.0)
        mainSpawn.room.showCreepList(Role.REPAIRER, listX, listY + 4.0)

        // Show prospector info
        val prospectorX = 14.0
        val prospectorY = 2.5
        Game.creeps.values.filter {
            it.memory.role in arrayOf(
                Role.PROSPECTOR,
                Role.SETTLER,
                Role.OUTER_HARVESTER,
                Role.CARAVAN,
                Role.RANGER,
                Role.BOUNCER,
            )
        }
            .sortedBy { it.memory.role }
            .forEachIndexed { index, it ->
                mainSpawn.room.showProspectorInfo(it, prospectorX, prospectorY + index * 1)
            }


        if (Memory.visualizeRepairs)
            Game.rooms.values.forEach {
                it.visualizeRepairs(4000)
            }

        // Show dying creeps
        if (Memory.visualizeDyingCreeps) {
            val dyingCreeps = mainSpawn.room.find(FIND_CREEPS).filter { it.ticksToLive < 100 }
            dyingCreeps.forEach {
                mainSpawn.room.visual.circle(it.pos, options { radius = 0.5; fill = "#FF0000"; opacity = 0.5 })
            }
        }

    }

    // Show graph for reach owned room
    Game.rooms.values.forEach {
        val y = if (it.memory.graphOnTopSide) 10.0 else 48.0
        if (Memory.recordData)
            recordGraph(it, 15, 15.0, y, showVis = Memory.visualizeGraphs && Memory.enableVisualizations && !saveCPU)
    }

    if (Memory.outputStructureCache)
        cacheStatus()

    // Show CPU usage
    if (Memory.outputCPUUsage) {
        console.log("Post-Processing CPU: ${Game.cpu.getUsed() - cpuBegin}")
        console.log("Main Loop CPU: ${Game.cpu.getUsed() - cpuInitial}")
        console.log("Total CPU: ${Game.cpu.getUsed()}")
    }

    // Send notification if CPU bucket is low
    if (Game.cpu.bucket < 8000) {
        Game.notify("CPU bucket is low: ${Game.cpu.bucket}", 60)
    }
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
    val creepColor = creep.pathColor()
    // Say the prospector name, inventory content, and distance from spawn
    val prospectorName = creep.name
    val prospectorInventory = "${creep.store.getUsedCapacity()}/${creep.store.getCapacity()}"
    val prospectorDistance = 80 - creep.pos.x - if (creep.room == this) 50 else 0//creep.pos.getRangeTo(this.find(FIND_MY_SPAWNS)[0])
    this.visual.text("${creep.memory.role.lowercase().capitalize()} $prospectorName", x, y,
        options { color = creepColor; align = TEXT_ALIGN_LEFT; font = "0.5"; opacity = 0.7 })
    /*this.visual.text("$prospectorInventory, d$prospectorDistance", x, y + 0.75,
        options { color = creepColor; align = TEXT_ALIGN_LEFT; font = "0.5"; opacity = 0.7 })*/
    //val lastTripDuration = creep.memory.lastTripDuration
    /*this.visual.text("${creep.memory.prospectedCount}T, time: $lastTripDuration", x, y + 1.5,
        options { color = "#AAAAAA"; align = TEXT_ALIGN_LEFT; font = "0.5" })*/
}

private fun houseKeeping(creeps: Record<String, Creep>) {
    if (Game.creeps.isEmpty()) return  // this is needed because Memory.creeps is undefined

    for ((creepName, _) in Memory.creeps) {
        if (creeps[creepName] == null) {
            console.log("deleting obsolete memory entry for creep $creepName")
            unassignSource(creepName)
            for (room in Game.rooms.values)
                unassignDistribution(creepName, room)
            if (Memory.creeps[creepName]?.role == Role.HARVESTER)
                Memory.forceReassignSources = true
            if (Memory.creeps[creepName]?.role == Role.CARRIER)
                Memory.forceReassignDistributionCategories = true

            // If prospector, send report
            /*if (Memory.creeps[creepName]?.role == Role.PROSPECTOR) {
                Game.notify("${getSKTime()} Prospector $creepName died, made ${Memory.creeps[creepName]?.prospectedCount} trips")
            }*/

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
        val areInvaders = username == "Invader"
        //if (username != "Invader" && !(Memory.ignorePlayers.contains(username)))
        Game.notify("${getSKTime()} $username spotted in room ${room.name}")
        if (!(Memory.ignorePlayers.contains(username)))
            towers.forEach { tower -> tower.attack(hostiles[0]) }
        // ACTIVATE SAFE MODE
        val spawn = room.find(FIND_MY_SPAWNS).firstOrNull()
        val hostilesTooFarIn = hostiles.filter { it.pos.getRangeTo(room.controller!!.pos) < 5 || (spawn?.pos?.getRangeTo(it.pos) ?: 10) < 5 }
        if (!areInvaders && hostilesTooFarIn.isNotEmpty() && room.controller?.safeModeAvailable > 0)
            room.controller?.activateSafeMode()
    } else if (damagedCreeps.isNotEmpty()) {
        towers.forEach { tower -> tower.heal(damagedCreeps[0]) }
    } else {
        val beginCPU = Game.cpu.getUsed()
        val repairTarget = room.getTowerRepairTarget()
        if (repairTarget != null) // all towers repair the same target
            towers.forEach { it.repair(repairTarget) }
        if (Memory.outputCPUUsage) // TODO Collapse this into a single print
            console.log("Tower Repair CPU: ${Game.cpu.getUsed() - beginCPU}")
    }
}

fun Room.visualizeRepairs(showUnder: Int) {
    val toBeRepaired = this.find(FIND_STRUCTURES, options { filter = { it.hits < it.hitsMax && it.hits < showUnder } })
    toBeRepaired.forEach { this.visual.circle(it.pos, options { radius = 0.5; fill = "#ff0000"; opacity = 0.2 }) }
}
