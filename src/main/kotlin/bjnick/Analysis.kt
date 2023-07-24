package bjnick

import assignedSource
import getAssignedHarvesterArray
import getHarvesterSpaces
import harvesterWorkParts
import optimalHarvesters
import screeps.api.*
import screeps.api.structures.StructureContainer
import setAssignedHarvesterArray
import setHarvesterSpaces
import kotlin.math.ceil
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
        this.memory.harvesterWorkParts = workParts
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
        this.visual.text("$assigned/$optimal", it.pos.x.toDouble(), it.pos.y.toDouble()+offset, options { color = textColor })
        // Also display # of energy left
        val energyLeft = it.energy
        val textColor2 = if (energyLeft > 300) "#AAAAAA" else "#FFAAAA"
        this.visual.text("$energyLeft", it.pos.x.toDouble()-1, it.pos.y.toDouble(), options { color = textColor2; font = "0.5" })
        // Also display amount in close container
        val containers = it.pos.findInRange(FIND_STRUCTURES, 1, options { filter = { it.structureType == STRUCTURE_CONTAINER } })
        if (containers.isNotEmpty()) {
            val container = containers.first() as StructureContainer
            val textColor3 = if (container.store[RESOURCE_ENERGY] > 300) "#FFFFAA" else "#FFAAAA"
            this.visual.text("${container.store[RESOURCE_ENERGY]}", it.pos.x.toDouble()-1, it.pos.y.toDouble()+0.5, options { color = textColor3; font = "0.5" })
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
    // Sort by distance to creep
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
