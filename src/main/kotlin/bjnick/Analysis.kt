package bjnick

import DataPoint
import assignedSource
import energyGraphData
import getAssignedHarvesterArray
import getHarvesterSpaces
import harvesterWorkParts
import optimalHarvesters
import screeps.api.*
import screeps.api.structures.StructureContainer
import setAssignedHarvesterArray
import setHarvesterSpaces
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Function to count empty spaces around a source, and save info about it
fun Source.harvesterSpaces(): Int {
    if (getHarvesterSpaces() != -1) {
        return getHarvesterSpaces()
    }
    var count = 0
    for (x in -1..1) {
        for (y in -1..1) {
            if (x != 0 || y != 0) {
                val terrainAt = room.getTerrain()[this.pos.x + x, this.pos.y + y]
                if (terrainAt.value and TERRAIN_MASK_WALL.value == 0) {
                    count++ // Ignore walls with roads in them
                }
            }
        }
    }
    setHarvesterSpaces(count)
    return count
}


// Returns minimum number of harvesters needed to exhaust the source
fun Source.minHarvestersBySpeed(workParts: Int): Int {
    val energyPerTick = 10 // per source
    return (ceil(energyPerTick / (2f * workParts))).toInt()
}

// Combined function to get the optimal number of workers for a source
fun Source.optimalHarvesters(workParts: Int): Int {
    return min(minHarvestersBySpeed(workParts), harvesterSpaces())
}

// Function to count empty spaces around all sources
// This is the optimal number of harvesters that can be assigned in a room
fun Room.optimalHarvesters(workParts: Int): Int {
    if (this.memory.optimalHarvesters == -1 || this.memory.harvesterWorkParts != workParts) {
        this.memory.optimalHarvesters = this.find(FIND_SOURCES).sumOf { it.optimalHarvesters(workParts) }
        //this.memory.harvesterWorkParts = workParts
    }
    return this.memory.optimalHarvesters
}

// Visualize the number of harvesters assigned to each source
fun Room.visualizeSources(): Unit {
    if (this.memory.harvesterWorkParts == -1) {
        optimalHarvesters(1)
    }
    this.find(FIND_SOURCES).forEach {
        val optimal = it.optimalHarvesters(memory.harvesterWorkParts)
        val assigned = it.getAssignedHarvesterArray().size
        val textColor = if (optimal == assigned) "#00FF00" else if (optimal < assigned) "#FFFF00" else "#AAAAAA"
        val offset = if (it.room.getTerrain().get(it.pos.x,it.pos.y+1) == TERRAIN_MASK_NONE) -0.6 else 0.6+0.45
        val toTheLeft = if (it.room.getTerrain().get(it.pos.x-1,it.pos.y) == TERRAIN_MASK_NONE) 1 else -1
        this.visual.text("$assigned/$optimal", it.pos.x.toDouble(), it.pos.y.toDouble()+offset, options { color = textColor })
        // Also display # of energy left
        val energyLeft = it.energy
        val textColor2 = if (energyLeft > 300) "#AAAAAA" else "#FFAAAA"
        this.visual.text("$energyLeft", it.pos.x.toDouble()+toTheLeft, it.pos.y.toDouble(), options { color = textColor2; font = "0.5" })
        // Also display amount in close container
        val containers = it.pos.findInRange(FIND_STRUCTURES, 1, options { filter = { it.structureType == STRUCTURE_CONTAINER } })
        if (containers.isNotEmpty()) {
            val container = containers.first() as StructureContainer
            val textColor3 = if (container.store[RESOURCE_ENERGY] > 300) "#FFFFAA" else "#FFAAAA"
            this.visual.text("${container.store[RESOURCE_ENERGY]}", it.pos.x.toDouble()+toTheLeft, it.pos.y.toDouble()+0.5, options { color = textColor3; font = "0.5" })
        }

    }
}

// TODO move in a more appropriate place
// Picks an assignment source for a harvester
fun Room.pickSourceForHarvester(creep: Creep): Source {
    val sources = this.find(FIND_SOURCES)
    // Recalculate based on work parts of the new creep
    // ACTUALLY DO NOT TO MAKE CONSISTENT
    //val newWorkParts = creep.body.count() { it.type == WORK }
    //this.optimalHarvesters(newWorkParts)
    // Sort by distance to SELF!!
    val sortedSources = sources.sortedBy { creep.pos.getRangeTo(it.pos) }
    // Find the first source that has less than optimal harvesters assigned
    val source = sortedSources.firstOrNull { it.optimalHarvesters(memory.harvesterWorkParts) > it.getAssignedHarvesterArray().size }
    // If null, then just get the closest source
        ?: return sortedSources.first()
    return source
}

fun Room.assignSource(creep: Creep, source: Source) {
    creep.memory.assignedSource = source.id
    source.setAssignedHarvesterArray(source.getAssignedHarvesterArray().plus(creep.name))
}

fun unassignSource(creepName: String) {
    // Dealing with dead creep
    val sourceID = Memory.creeps[creepName]?.assignedSource
    val sourceObj = Game.getObjectById<Source>(sourceID) ?: return
    sourceObj.setAssignedHarvesterArray(sourceObj
        .getAssignedHarvesterArray().filterNot { it == creepName }.toTypedArray())
    Memory.creeps[creepName]?.assignedSource = ""
}


fun Room.placeExtensionSites(count: Int) {
    // Find a place around the spawner to place a new extension
    val spawner = this.find(FIND_MY_SPAWNS).first()
    var placed = 0
    for (radius in 1..5) {
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                if (placed >= count) {
                    return
                }
                if ((x == radius || y == radius) && x%2 == y%2) {
                    val terrainAt = this.getTerrain()[spawner.pos.x + x, spawner.pos.y + y]
                    val structures = this.lookForAt(LOOK_STRUCTURES, spawner.pos.x + x, spawner.pos.y + y)?.firstOrNull()
                    val constructionSites = this.lookForAt(LOOK_CONSTRUCTION_SITES, spawner.pos.x + x, spawner.pos.y + y)?.firstOrNull()
                    if (terrainAt.value and TERRAIN_MASK_WALL.value == 0 && structures == null && constructionSites == null) {
                        //createFlag(spawner.pos.x + x, spawner.pos.y + y, "ExtensionSite")
                        val err = this.createConstructionSite(spawner.pos.x + x, spawner.pos.y + y, STRUCTURE_EXTENSION)
                        if (err != OK) {
                            return
                        }
                        placed++
                    }
                }
            }
        }
    }
}

fun recordImportExport(from: String, to: String, amount: Int)  {
    if (Game.rooms[from] != null && Game.rooms[from]!!.memory.energyGraphData.isNotEmpty()) {
        val room = Game.rooms[from]!!
        val lastPoint = room.memory.energyGraphData[room.memory.energyGraphData.size - 1]
        room.memory.energyGraphData[room.memory.energyGraphData.size - 1] =
            DataPoint(
                lastPoint.sourceEnergy,
                lastPoint.extensionEnergy,
                lastPoint.containerEnergy,
                lastPoint.time,
                lastPoint.imports,
                lastPoint.exports + amount,
            )
    }
    if (Game.rooms[to] != null && Game.rooms[to]!!.memory.energyGraphData.isNotEmpty()) {
        val room = Game.rooms[to]!!
        val lastPoint = room.memory.energyGraphData[room.memory.energyGraphData.size - 1]
        room.memory.energyGraphData[room.memory.energyGraphData.size - 1] =
            DataPoint(
                lastPoint.sourceEnergy,
                lastPoint.extensionEnergy,
                lastPoint.containerEnergy,
                lastPoint.time,
                lastPoint.imports + amount,
                lastPoint.exports,
            )
    }
}

fun recordGraph(room: Room, every: Int, x: Double, y: Double, showVis: Boolean = true) {
    // Then graph the last 5 points (if there are 5 points)
    val maxPoints = 30

    val extensionEnergy = room.energyAvailable
    val containerEnergy = room.find(FIND_STRUCTURES, options { filter = { it.structureType == STRUCTURE_CONTAINER } })
        .sumOf { it.unsafeCast<StoreOwner>().store[RESOURCE_ENERGY] ?: 0 }
    val sourceEnergy = room.find(FIND_SOURCES).sumOf { it.energy }
    // If Game.time % N == 0, then record new point, else add to last point
    if (Game.time % every == 0) {
        val combined = DataPoint(sourceEnergy/every, extensionEnergy/every, containerEnergy/every,
            Game.time, 0, 0)
        room.memory.energyGraphData += combined
    } else {
        if (room.memory.energyGraphData.isNotEmpty()) {
            val lastPoint = room.memory.energyGraphData[room.memory.energyGraphData.size - 1]
            room.memory.energyGraphData[room.memory.energyGraphData.size - 1] =
                DataPoint(
                    lastPoint.sourceEnergy + sourceEnergy / every,
                    lastPoint.extensionEnergy + extensionEnergy / every,
                    lastPoint.containerEnergy + containerEnergy / every, lastPoint.time,
                    lastPoint.imports,
                    lastPoint.exports,
                )
        }
    }
    if (room.memory.energyGraphData.isEmpty() || !showVis) {
        return
    }

    // Trim Memory to max points
    room.memory.energyGraphData = room.memory.energyGraphData.takeLast(maxPoints+1).toTypedArray()
    val separation = 1
    val xOffset = x-maxPoints*separation/2

    val extensionPolyLine = room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .mapIndexed { index, dataPoint -> arrayOf(index*separation+xOffset, -dataPoint.extensionEnergy*0.003+y)
    }.toTypedArray()
    room.visual.poly(extensionPolyLine, options { stroke = "#FFFF00"; opacity = 0.5 })

    val sourcePolyLine = room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .mapIndexed { index, dataPoint -> arrayOf(index*separation+xOffset, -dataPoint.sourceEnergy*0.001+y)
        }.toTypedArray()
    room.visual.poly(sourcePolyLine, options { stroke = "#FFFFFF"; opacity = 0.5 })

    val containerPolyLine = room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .mapIndexed { index, dataPoint -> arrayOf(index*separation+xOffset, -dataPoint.containerEnergy*0.001+y)
        }.toTypedArray()
    room.visual.poly(containerPolyLine, options { stroke = "#FFAA00"; opacity = 0.5 })

    val importsPolyLine = room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .mapIndexed { index, dataPoint -> arrayOf(index*separation+xOffset, -dataPoint.imports*0.003+y)
        }.toTypedArray()
    room.visual.poly(importsPolyLine, options { stroke = "#00FF00"; opacity = 0.5 })

    val exportsPolyLine = room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .mapIndexed { index, dataPoint -> arrayOf(index*separation+xOffset, -dataPoint.exports*0.003+y)
        }.toTypedArray()
    room.visual.poly(exportsPolyLine, options { stroke = "#FF0000"; opacity = 0.5 })

    val maxStartPointHeight = min(min(extensionPolyLine[0][1], sourcePolyLine[0][1]), containerPolyLine[0][1])
    room.visual.text("Source", xOffset, maxStartPointHeight-1-1.5,
        options { color = "#FFFFFF"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("Extension", xOffset, maxStartPointHeight-0.5-1.5,
        options { color = "#FFFF00"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("Container", xOffset, maxStartPointHeight-0-1.5,
        options { color = "#FFAA00"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("Imports", xOffset, maxStartPointHeight+0.5-1.5,
        options { color = "#00FF00"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("Exports", xOffset, maxStartPointHeight+1.0-1.5,
        options { color = "#FF0000"; font = "0.5"; align = TEXT_ALIGN_LEFT })

    val drawnPoints = extensionPolyLine.size
    val lastExtensionEnergy = room.memory.energyGraphData[room.memory.energyGraphData.size-2].extensionEnergy
    val lastSourceEnergy = room.memory.energyGraphData[room.memory.energyGraphData.size-2].sourceEnergy
    val lastContainerEnergy = room.memory.energyGraphData[room.memory.energyGraphData.size-2].containerEnergy
    val lastImports = room.memory.energyGraphData[room.memory.energyGraphData.size-2].imports ?: 0
    val lastExports = room.memory.energyGraphData[room.memory.energyGraphData.size-2].exports ?: 0

    // Add label to last point
    room.visual.text("$lastExtensionEnergy", extensionPolyLine[drawnPoints-1][0]+0.25,
        extensionPolyLine[drawnPoints-1][1]+0.125,
        options { color = "#FFFF00"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("$lastSourceEnergy", sourcePolyLine[drawnPoints-1][0]+0.25,
        sourcePolyLine[drawnPoints-1][1]+0.125,
        options { color = "#FFFFFF"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("$lastContainerEnergy", containerPolyLine[drawnPoints-1][0]+0.25,
        containerPolyLine[drawnPoints-1][1]+0.125,
        options { color = "#FFAA00"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("$lastImports", importsPolyLine[drawnPoints-1][0]+0.25,
        importsPolyLine[drawnPoints-1][1]+0.125,
        options { color = "#00FF00"; font = "0.5"; align = TEXT_ALIGN_LEFT })
    room.visual.text("$lastExports", exportsPolyLine[drawnPoints-1][0]+0.25,
        exportsPolyLine[drawnPoints-1][1]+0.125,
        options { color = "#FF0000"; font = "0.5"; align = TEXT_ALIGN_LEFT })

    // Draw some tickmark points (displaying "every")
    for (i in 0 until drawnPoints) {
        room.visual.text("${-(drawnPoints-i-1)*every}", i*separation+xOffset, y+0.5,
            options { color = "#FFFFFF"; font = "0.25"; align = TEXT_ALIGN_CENTER })
    }

    // Show a counter until next data point
    val counter = every - Game.time % every
    room.visual.text("$counter", (drawnPoints-1)*separation+xOffset, y+0.5+0.75,
        options { color = "#FFFFFF"; font = "0.5"; align = TEXT_ALIGN_CENTER })

    // Show import/export amount in the past cycle of 300 ticks
    val importRate = (room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .sumOf { it.imports }) * 300 / (every*maxPoints)
    val exportRate = (room.memory.energyGraphData.dropLast(1).takeLast(maxPoints)
        .sumOf { it.exports }) * 300 / (every*maxPoints)

    room.visual.text("Import rate: $importRate/cycle", x, y-9, options { color = "#00FF00"; font = "0.7"; align = TEXT_ALIGN_CENTER })
    room.visual.text("Export rate: $exportRate/cycle", x, y-8, options { color = "#FF0000"; font = "0.7"; align = TEXT_ALIGN_CENTER })

}

