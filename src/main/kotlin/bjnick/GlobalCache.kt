package bjnick

import screeps.api.FIND_STRUCTURES
import screeps.api.Game
import screeps.api.Room
import screeps.api.StructureConstant
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




