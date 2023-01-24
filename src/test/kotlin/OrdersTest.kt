import functional.*
import kotlin.test.Test
import kotlin.test.assertEquals

class OrdersTest {

    class FakeCreep(override val energyCarried: Int=0, override val spaceAvailable: Int=0) : CreepCarryingEnergy() {
        override fun toString(): String = "Creep(energyCarried=$energyCarried, spaceAvailable=$spaceAvailable)"
    }

    @Test
    fun testAssignCreepsToFeed() {
        // Test assignCreepsToFeed function
        val carryingEnergyList = mutableListOf<CreepCarryingEnergy>()
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

        val alreadyAssigned = mutableListOf<AssignedTask>()
        alreadyAssigned.add(AssignedTask("B", FakeCreep(50)))
        alreadyAssigned.add(AssignedTask("C", FakeCreep(100)))

        val filteredOrders = subtractAlreadyAssigned(feedOrders, alreadyAssigned)
        val assignments = assignCreepsToFeed(carryingEnergyList, filteredOrders)

        println(assignments.toString().replace(")), ", ")),\n"));
    }

    @Test
    fun testAssignCreepsToCollect() {
        // Test assignCreepsToCollect function
        val carryingEnergyList = mutableListOf<CreepCarryingEnergy>()
        carryingEnergyList.add(FakeCreep(spaceAvailable=20))
        carryingEnergyList.add(FakeCreep(spaceAvailable=40))
        carryingEnergyList.add(FakeCreep(spaceAvailable=100))
        carryingEnergyList.add(FakeCreep(spaceAvailable=0))
        carryingEnergyList.add(FakeCreep(spaceAvailable=100))
        carryingEnergyList.add(FakeCreep(spaceAvailable=30))
        carryingEnergyList.add(FakeCreep(spaceAvailable=70))

        val collectOrders = mutableListOf<CollectOrder>()
        collectOrders.add(CollectOrder( 35, "A", 1))
        collectOrders.add(CollectOrder(90, "B", 2))
        collectOrders.add(CollectOrder(120, "C", 3))
        collectOrders.add(CollectOrder(10, "D", 4))
        collectOrders.add(CollectOrder(200, "E", 5))

        val alreadyAssigned = mutableListOf<AssignedTask>()
        alreadyAssigned.add(AssignedTask("B", FakeCreep(spaceAvailable=50)))
        alreadyAssigned.add(AssignedTask("C", FakeCreep(spaceAvailable=100)))

        val filteredOrders = subtractAlreadyAssigned(collectOrders, alreadyAssigned)
        val assignments = assignCreepsToCollect(carryingEnergyList, filteredOrders)

        println(assignments.toString().replace(")), ", ")),\n"));
    }


    @Test
    fun testSubtractAssignedFeedTasks() {

        val feedOrders = mutableListOf<FeedOrder>()
        feedOrders.add(FeedOrder( 35, "A", 1))
        feedOrders.add(FeedOrder(90, "B", 2))
        feedOrders.add(FeedOrder(120, "C", 3))

        val alreadyAssigned = mutableListOf<AssignedTask>()
        alreadyAssigned.add(AssignedTask("B", FakeCreep(100)))
        alreadyAssigned.add(AssignedTask("C", FakeCreep(100)))

        val filteredOrders = subtractAlreadyAssigned(feedOrders, alreadyAssigned)

        assertEquals(2, filteredOrders.size)
        assertEquals(FeedOrder(35, "A", 1), filteredOrders[0])
        assertEquals(FeedOrder(20, "C", 3), filteredOrders[1])
    }

    @Test
    fun testSubtractAssignedCollectTasks() {

        val collectOrders = mutableListOf<CollectOrder>()
        collectOrders.add(CollectOrder( 35, "A", 1))
        collectOrders.add(CollectOrder(90, "B", 2))
        collectOrders.add(CollectOrder(120, "C", 3))

        val alreadyAssigned = mutableListOf<AssignedTask>()
        alreadyAssigned.add(AssignedTask("B", FakeCreep(spaceAvailable=100)))
        alreadyAssigned.add(AssignedTask("C", FakeCreep(spaceAvailable=100)))

        val filteredOrders = subtractAlreadyAssigned(collectOrders, alreadyAssigned)

        assertEquals(2, filteredOrders.size)
        assertEquals(CollectOrder(35, "A", 1), filteredOrders[0])
        assertEquals(CollectOrder(20, "C", 3), filteredOrders[1])
    }


}