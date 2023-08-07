package bjnick

import AbstractPos
import absToPos
import intentID
import intentPos
import intentResourceType
import intentType
import screeps.api.*
import screeps.api.structures.Structure


val _structures: HashMap<Pair<String, StructureConstant>, Array<String>> = HashMap()

fun cachedStructures(room: Room, type: StructureConstant): Array<Structure> {
    return room.find(FIND_STRUCTURES).filter { it.structureType == type }.filterNotNull().toTypedArray()
    //  TODO INEFFICIENT
    /*val key = Pair(room.name, type)
    if (_structures.containsKey(key)) return _structures[key]!!.map { Game.getObjectById<Structure>(it) }.filterNotNull().toTypedArray()
    val newStructures = room.find(FIND_STRUCTURES).filter { it.structureType == type }.filterNotNull().toTypedArray()
    console.log("Structure cache reset for ${room.name} -> ${type}")
    _structures[key] = newStructures.map { it.id }.toTypedArray()
    return newStructures*/
}

// TODO Reset when new structures are built
fun resetCachedStructures(room: Room, type: StructureConstant) {
    val key = Pair(room.name, type)
    console.log("Structure cache reset for ${room.name} -> ${type}")
    _structures.remove(key)
}

fun cacheStatus() {
    console.log("Structure cache status:")
    for (key in _structures.keys) {
        console.log("  ${key.first} -> ${key.second}: ${_structures[key]!!.size}")
    }
}

enum class IntentType {
    None,
    PutEnergy,
    PutResource,
    Repair,
    CollectEnergy,
    CollectResource,
}

typealias ID = String
typealias CreepName = String

//private val cachedIntents: MutableMap<CreepName, Triple<IntentType, ID, RoomPosition>?> = mutableMapOf()

data class IntentData(val type: IntentType, val id: ID, val pos: RoomPosition, val resourceType: ResourceConstant)

fun Creep.getCachedIntent(): IntentData? {
    // If the intent is present in the cache, return it
    // if it is of type None, remove it from the cache and return null

    /*val cachedVal = cachedIntents[this.name]
    if (cachedVal != null) {
        if (cachedVal.first == IntentType.None) {
            cachedIntents.remove(this.name)
            return null
        }
        return cachedVal
    }*/

    // If the intent is not present in the cache, check if it is present in memory
    // if it is of type None, return null
    val type = IntentType.values()[this.memory.intentType]
    if (type != IntentType.None) {
        val pos = absToPos(this.memory.intentPos)
        val id = this.memory.intentID
        val resourceType = this.memory.intentResourceType
        return IntentData(type, id, pos, resourceType)
    } else {
        return null
    }
}

fun Creep.setCachedIntent(type: IntentType, id: ID, pos: RoomPosition, resourceType: ResourceConstant = RESOURCE_ENERGY) {
    //cachedIntents[this.name] = Triple(type, id, pos)
    this.memory.intentType = type.ordinal
    this.memory.intentID = id
    this.memory.intentPos = AbstractPos(pos)
    this.memory.intentResourceType = resourceType
}

/** Returns true if executing and is busy */
fun Creep.executeCachedIntent(): Boolean {
    // TODO Enable
    return false
    val intent = getCachedIntent() ?: return false
    if (intent.type == IntentType.None) return false
    say(IntentType.values()[memory.intentType].name) // TODO Debug only
    console.log("$name executing intent: ${intent.type} ${intent.id} ${intent.pos}")
    val gameObject = Game.getObjectById<Identifiable>(id)?.unsafeCast<HasPosition>() ?: return false
    when (intent.type) {
        IntentType.None -> return false
        IntentType.CollectEnergy -> collectEnergyFrom(gameObject, false)
        IntentType.CollectResource -> collectResourceFrom(gameObject, intent.resourceType, false)
        IntentType.PutEnergy -> putEnergy(gameObject, false)
        IntentType.PutResource -> putResource(gameObject, intent.resourceType, false)
        IntentType.Repair -> goRepair(gameObject.unsafeCast<Structure>(), false)
    }
    return true
}

fun Creep.resetCachedIntent() {
    //cachedIntents.remove(this.name)
    this.memory.intentType = 0
    this.memory.intentID = ""
    this.memory.intentPos = AbstractPos(0, 0, "")
    this.memory.intentResourceType = RESOURCE_ENERGY
}


