import functional.DEBUG_MODE
import functional.gameLoop
import kotlin.test.Test

class GameLoopTest {

    @Test
    fun testGameLoop() {
        println("Starting game loop test...\n")

        DEBUG_MODE = true
        gameLoop()

        println("\nGame loop test complete.")
    }

}