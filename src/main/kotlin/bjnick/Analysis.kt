package bjnick

import assignedSource
import getAssignedHarvesterArray
import getHarvesterSpaces
import harvesterWorkParts
import optimalHarvesters
import screeps.api.*
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
        this.visual.text("$assigned/$optimal", it.pos.x.toDouble(), it.pos.y.toDouble()-0.4, options { color = textColor })
    }
}

// TODO move in a more appropriate place
// Picks an assignment source for a harvester
fun Room.pickSourceForHarvester(creep: Creep): Source {
    val sources = this.find(FIND_SOURCES)
    // Recalculate based on work parts of the new creep
    val newWorkParts = creep.body.count() { it.type == WORK }
    this.optimalHarvesters(newWorkParts)
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

