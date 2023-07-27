
import bjnick.Role
import screeps.api.*
import screeps.utils.memory.memory

/* Add the variables that you want to store to the persistent memory for each object type.
* They can be accessed by using the .memory attribute of any of the instances of that class
* i.e. creep.memory.building = true */

/* Creep.memory */
var CreepMemory.collecting: Boolean by memory { false }
var CreepMemory.role: String by memory { "" }
var CreepMemory.distributesEnergy: Boolean by memory { false }

var CreepMemory.distributionCategory: Int by memory { 0 }

class AbstractPos(val x: Int, val y: Int, val roomName: String) {
    constructor(pos: RoomPosition) : this(pos.x, pos.y, pos.roomName)
}

fun absToPos(absPos: AbstractPos) = RoomPosition(absPos.x, absPos.y, absPos.roomName)

var CreepMemory.targetID : String by memory { "" }
var CreepMemory.targetPos : AbstractPos by memory { AbstractPos(0, 0, "") }
var CreepMemory.targetTask : String by memory { "" }

var CreepMemory.assignedSource : String by memory { "" }
var CreepMemory.collectingPriority : String by memory { "" }

var CreepMemory.assignedRoom : String by memory { "" }
var CreepMemory.homeRoom : String by memory { "" }
var CreepMemory.prospectedCount : Int by memory { 0 }
var CreepMemory.lastTripStarted : Int by memory { 0 }
var CreepMemory.lastTripDuration : Int by memory { 0 }

var CreepMemory.lastRoom: String by memory { "" }

var CreepMemory.preferredPathCache: Int by memory { 0 }

val FLAG_IGNORE = 0
val FLAG_COLLECTING = 1
val FLAG_DISTRIBUTING = 2

var CreepMemory.collectingFlag: Int by memory { FLAG_IGNORE }

/* Rest of the persistent memory structures.
* These set an unused test variable to 0. This is done to illustrate the how to add variables to
* the memory. Change or remove it at your convenience.*/

/* Power creep is a late game hero unit that is spawned from a Power Spawn
   see https://docs.screeps.com/power.html for more details.
   This set sets up the memory for the PowerCreep.memory class.
 */
//var PowerCreepMemory.test: Int by memory { 0 }

/* flag.memory */
//var FlagMemory.test: Int by memory { 0 }

/* room.memory */
var RoomMemory.numberOfCreeps: Int by memory { 0 }

var RoomMemory.collectData: Boolean by memory { false }

var RoomMemory.optimalHarvesters: Int by memory { -1 }
var RoomMemory.harvesterWorkParts: Int by memory { -1 }

// Source memory: using Room memory
var RoomMemory.sourceIDs: Array<String> by memory { arrayOf() }
var RoomMemory.sourceHarvesterSpaces: Array<Int> by memory { arrayOf() }
var RoomMemory.sourceAssignedHarvesters: Array<Array<String>> by memory { arrayOf() }

var RoomMemory.distributionAssignments: Array<String> by memory { arrayOf() }

var RoomMemory.graphOnTopSide: Boolean by memory { false }

// GLOBAL memory

var Memory.forceReassignSources: Boolean by memory { false }
var Memory.forceReassignDistributionCategories: Boolean by memory { false }

var Memory.prospectingTargets: Array<String> by memory { arrayOf() }
var Memory.prospectingRooms: Array<String> by memory { arrayOf() }

var Memory.homeRoom: String by memory { "" }
var Memory.settlementRoom: String by memory { "" }

var Memory.defendRoom: String by memory { "" }

var Memory.ignorePlayers: Array<String> by memory { arrayOf() }

var Memory.visualizeRepairs: Boolean by memory { false }
var Memory.visualizeDyingCreeps: Boolean by memory { true }

var Memory.outputCPUUsage: Boolean by memory { false }
var Memory.visualizeCPUUsage: Boolean by memory { false }

var Memory.enableVisualizations: Boolean by memory { false }
var Memory.visualizeGraphs: Boolean by memory { false }

class DataPoint(val sourceEnergy: Int = 0, val extensionEnergy: Int = 0, val containerEnergy: Int = 0, val time: Int = 0, val imports: Int = 0, val exports: Int = 0)

var RoomMemory.energyGraphData: Array<DataPoint> by memory { arrayOf() }

/* spawn.memory */
//var SpawnMemory.test: Int by memory { 0 }


fun Room.initializeSourceMemory() {
    if (this.memory.sourceIDs.isNotEmpty()) return
    val sourceCount = this.find(FIND_SOURCES).size
    this.memory.sourceIDs = this.find(FIND_SOURCES).map { it.id }.toTypedArray()
    this.memory.sourceHarvesterSpaces = Array(sourceCount) { -1 }
    this.memory.sourceAssignedHarvesters = Array(sourceCount) { arrayOf() }
}


fun Source.getHarvesterSpaces(): Int {
    room.initializeSourceMemory()
    val room = Game.rooms[this.room.name] ?: return -1
    val index = room.memory.sourceIDs.indexOf(this.id)
    if (index == -1) return -1
    return room.memory.sourceHarvesterSpaces[index]
}

fun Source.setHarvesterSpaces(value: Int) {
    room.initializeSourceMemory()
    val index = room.memory.sourceIDs.indexOf(this.id)
    if (index == -1) {
        room.memory.sourceIDs += this.id
        room.memory.sourceHarvesterSpaces += value
        room.memory.sourceAssignedHarvesters += arrayOf()
    } else {
        room.memory.sourceHarvesterSpaces[index] = value
    }
}

fun Source.getAssignedHarvesterArray(): Array<String> {
    room.initializeSourceMemory()
    val index = room.memory.sourceIDs.indexOf(this.id)
    if (index == -1) return arrayOf()
    if (index > room.memory.sourceAssignedHarvesters.size - 1) {
        room.memory.sourceAssignedHarvesters += arrayOf()
    }
    return room.memory.sourceAssignedHarvesters[index]
}

fun Source.setAssignedHarvesterArray(value: Array<String>) {
    room.initializeSourceMemory()
    val index = room.memory.sourceIDs.indexOf(this.id)
    if (index == -1) {
        room.memory.sourceIDs += this.id
        room.memory.sourceHarvesterSpaces += 0
        room.memory.sourceAssignedHarvesters += arrayOf()
        room.memory.sourceAssignedHarvesters[room.memory.sourceAssignedHarvesters.size - 1] = value
    } else {
        room.memory.sourceAssignedHarvesters[index] = value
    }
}
