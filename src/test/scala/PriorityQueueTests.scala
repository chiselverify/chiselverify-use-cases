import Behavioural.heapifyUp
import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

class PriorityQueueTests extends FlatSpec with Matchers {
  val cWid = 2
  val nWid = 8
  val rWid = 5
  val heapSize = 9
  val chCount = 2
  val debugLvl = 2
  "HeapPrioQ" should "pass" in {
    chisel3.iotesters.Driver(() => new HeapPriorityQueue(heapSize,chCount,nWid,cWid,rWid)) {
      c => {
        val dut = new HeapPriorityQueueWrapper(c,heapSize,chCount,debugLvl)(cWid,nWid,rWid)
        val toBeInserted = Array(Array(2,55),Array(2,45),Array(0,240),Array(1,2))
        for(i <- 0 until 4) {
          dut.insert(toBeInserted(i)(0),toBeInserted(i)(1),i)
        }
        //TODO: an error occurs here when 1:2:3 is inserted in the first heapifier cycle
        dut.printMem()
        dut.remove(0)
        dut.printMem()
        dut.insert(0,239,11)
        dut.printMem()
        dut.remove(11)
        dut.printMem()
        dut.remove(1)
        dut.printMem()
        dut.remove(3)
        dut.printMem()
        dut.remove(2)
        dut.printMem()
        dut.insert(0,22,23)
        dut.printMem()
        dut.insert(1,22,21)
        dut.printMem()
        dut
      }
    } should be(true)
  }
}