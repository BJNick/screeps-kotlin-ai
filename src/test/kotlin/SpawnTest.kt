import screeps.api.*
import bjnick.bestWorker
import kotlin.test.Test
import kotlin.test.assertEquals

class SpawnTest {
    @Test
    fun makeBigWorker() {
        val result = bestWorker(550)
        assertEquals(result.size, 6)
        assertEquals(result.count { it == WORK }, 4)
        assertEquals(result.count { it == MOVE }, 1)
        assertEquals(result.count { it == CARRY }, 1)
        assertEquals(true, false)
    }

    @Test
    fun thingsShouldBreak() {
        assertEquals(listOf(1,2,3).reversed(), listOf(1,2,3))
    }

}