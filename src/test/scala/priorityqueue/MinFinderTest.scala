package priorityqueue

import chiseltest._
import priorityqueue.Helpers._
import org.scalatest._
import priorityqueue.PriorityQueueParameters
import priorityqueue.modules.MinFinder

/**
  * contains a randomized test for the MinFinder module
  */
class MinFinderTest extends FreeSpec with ChiselScalatestTester {

    def calculateOut(values: Seq[Seq[Int]]): Int = {
        val cyclic = values.map(_.head)
        val cyclicMins = cyclic.zipWithIndex.filter(_._1 == cyclic.min).map(_._2)
        if (cyclicMins.length == 1) {
            cyclicMins.head
        } else {
            val normals = values.map(_ (1))
            val candidates = Seq.tabulate(values.length)(i => if (cyclicMins.contains(i)) normals(i) else Int.MaxValue)
            candidates.indexOf(candidates.min)
        }
    }

    val n = 8
    implicit val parameters = PriorityQueueParameters(32,4,4,8,5)

    "MinFinder should identify minimum value with the lowest index" in {
        test(new MinFinder(n)) { dut =>

            import parameters._

            setWidths(superCycleWidth, cycleWidth, referenceIdWidth)

            for (i <- 0 until 1000) {

                val values = pokePrioAndIDVec(dut.io.values)

                assert(peekPrioAndId(dut.io.res) == values(calculateOut(values)),
                    s"\n${prioAndIdVecToString(values)} should be ${prioAndIdToString(values(calculateOut(values)))} received ${prioAndIdToString(peekPrioAndId(dut.io.res))}")

                assert(dut.io.index.peek.litValue == calculateOut(values),
                    s"\n${prioAndIdVecToString(values)} should be ${calculateOut(values)} received ${dut.io.index.peek.litValue}")
            }

        }
    }
}