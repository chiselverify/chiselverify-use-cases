package priorityqueue

import chiseltest._
import org.scalatest._
import priorityqueue.lib.Helpers._
import chisel3._
import priorityqueue.lib.PriorityType
import priorityqueue.lib.Parameters._
import models.MinFinderModel._
import modules.MinFinder
import chiselverify.coverage._

/**
 * contains a randomized test for the modules.MinFinder module
 */
class MinFinderTest extends FreeSpec with ChiselScalatestTester {

  val n = 4
  setParameters(4, 4, 64, 4)

  "MinFinder should identify highest priority" in {
    test(new MinFinder(n)) { dut =>

      val cr = new CoverageReporter(dut)
      cr.register(
          CoverPoint(dut.io.values(0).active, "active0")(
            Bins("inactive", 0 to 0) :: Bins("active", 1 to 1) :: Nil) ::
          CoverPoint(dut.io.values(1).active, "active1")(
            Bins("inactive", 0 to 0) :: Bins("active", 1 to 1) :: Nil) ::
          CoverPoint(dut.io.values(2).active, "active2")(
            Bins("inactive", 0 to 0) :: Bins("active", 1 to 1) :: Nil) ::
          CoverPoint(dut.io.values(3).active, "active3")(
            Bins("inactive", 0 to 0) :: Bins("active", 1 to 1) :: Nil) ::

          Nil,
          CrossPoint("0,1 active", "active0", "active1")(
            CrossBin("all", 0 to 1, 0 to 1) :: Nil) ::
          CrossPoint("0,2 active", "active0", "active2")(
            CrossBin("all", 0 to 1, 0 to 1) :: Nil) ::
          CrossPoint("0,3 active", "active0", "active3")(
            CrossBin("all", 0 to 1, 0 to 1) :: Nil) ::

          Nil)

      for (i <- 0 until 1000) {

        val values = Array.fill(n)(PriorityType())

        if(values.map(_.active).reduce((a, b) => a || b)){

          val smallest = randPow2Max(cWid)
          dut.io.currentSC.poke(smallest.U)
          dut.io.values.zip(values).foreach(p => p._1.poke(p._2.toHWType))

          val model = minFinder(values, smallest)

          val msg = s"${values.mkString(",")} with smallest $smallest should be ${model._2} received ${PriorityType(dut.io.res)}"

          cr.sample()

          assert(PriorityType(dut.io.res) == model._2, msg)

          assert(dut.io.idx.peek.litValue == model._1, msg)
        }
      }
      cr.printReport()
    }
  }

}