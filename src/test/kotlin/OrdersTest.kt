import functional.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OrdersTest {

    class FakeCreep(override val energyCarried: Int) : CarryingEnergy {
        override fun toString(): String = "Creep(energyCarried=$energyCarried)"
    }

    @Test
    fun testAssignCreepsToFeed() {
        // Test assignCreepsToFeed function
        val carryingEnergyList = mutableListOf<CarryingEnergy>()
        carryingEnergyList.add(FakeCreep(20))
        carryingEnergyList.add(FakeCreep(40))
        carryingEnergyList.add(FakeCreep(100))
        carryingEnergyList.add(FakeCreep(0))
        carryingEnergyList.add(FakeCreep(100))
        carryingEnergyList.add(FakeCreep(30))
        carryingEnergyList.add(FakeCreep(70))

        val feedOrders = mutableListOf<FeedOrder>()
        feedOrders.add(FeedOrder( 35, "A", 1))
        feedOrders.add(FeedOrder(90, "B", 2))
        feedOrders.add(FeedOrder(120, "C", 3))
        feedOrders.add(FeedOrder(10, "D", 4))
        feedOrders.add(FeedOrder(200, "E", 5))

        val alreadyAssigned = mutableListOf<AssignedFeedTask>()
        alreadyAssigned.add(AssignedFeedTask("B", 50))
        alreadyAssigned.add(AssignedFeedTask("C", 100))

        val filteredOrders = subtractAlreadyAssigned(feedOrders, alreadyAssigned)
        val assignments = assignCreepsToFeed(carryingEnergyList, filteredOrders)

        println(assignments.toString().replace(")), ", ")),\n"));
    }



    @Test
    fun testSubtractAssignedFeedTasks() {

        val feedOrders = mutableListOf<FeedOrder>()
        feedOrders.add(FeedOrder( 35, "A", 1))
        feedOrders.add(FeedOrder(90, "B", 2))
        feedOrders.add(FeedOrder(120, "C", 3))

        val alreadyAssigned = mutableListOf<AssignedFeedTask>()
        alreadyAssigned.add(AssignedFeedTask("B", 100))
        alreadyAssigned.add(AssignedFeedTask("C", 100))

        val filteredOrders = subtractAlreadyAssigned(feedOrders, alreadyAssigned)

        assertEquals(2, filteredOrders.size)
        assertEquals(FeedOrder(35, "A", 1), filteredOrders[0])
        assertEquals(FeedOrder(20, "C", 3), filteredOrders[1])

    }

}