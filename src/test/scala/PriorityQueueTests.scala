import Behavioural.heapifyUp
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

private class Test1(dut: HeapPrioQ, normalWidth: Int, cyclicWidth: Int, heapSize: Int, childrenCount: Int, referenceWidth: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {

  // setup random memory state
  val rand = scala.util.Random
  val priorities: Array[Array[Int]] = Array.fill(heapSize)(Array(Math.pow(2,cyclicWidth).toInt-1,Math.pow(2,normalWidth).toInt-1))
  val prioritiesOriginal = priorities.map(_.clone())
  // create 3d array. First index is RAM address, 2nd index selects element at RAM address, 3rd selects cyclic or normal priority value
  val Mem: Array[Array[Array[Int]]] = priorities.slice(1,priorities.length).sliding(childrenCount,childrenCount).toArray.map(_.map(_.clone()))
  // root/head element is not a part of RAM

  // simulate synchronous memory
  var lastReadAddr = 0
  var lastWriteAddr = 0

  // determine the last index which has children
  var lastParent = Seq.tabulate(heapSize)(i => (i * childrenCount)+1 < heapSize).lastIndexOf(true)
  // randomly set starting index
  val index = rand.nextInt(lastParent)

  // apply behavioral model to memory state
  heapifyUp(priorities, childrenCount, heapSize, index)

  // setup inputs of dut
  poke(dut.io.cmd.valid, false)
  poke(dut.io.cmd.op,1)
  poke(dut.io.cmd.refID,0)
  val vals = Array(Array(3,2),Array(2,20),Array(0,10))
  // loop variables
  for(i <- 0 until 3) {
    var iterations = 0
    //val toBeInserted = Array(rand.nextInt(Math.pow(2,cyclicWidth).toInt), rand.nextInt(Math.pow(2,normalWidth).toInt))
    val toBeInserted = vals(i)
    println(s"Inserting: ${toBeInserted.mkString(":")}----------------------------------")
    poke(dut.io.cmd.prio.cycl, toBeInserted(0))
    poke(dut.io.cmd.prio.norm, toBeInserted(1))
    poke(dut.io.cmd.valid, true)

    while (peek(dut.io.cmd.done).toInt == 0 || iterations < 1) {

      for (i <- 0 until childrenCount) {
        // ignores reads outside of array
        try {
          poke(dut.io.ramReadPort.data(i).cycl, Mem(lastReadAddr)(i)(0))
          poke(dut.io.ramReadPort.data(i).norm, Mem(lastReadAddr)(i)(1))
        } catch {
          case e: IndexOutOfBoundsException => {}
        }
      }
      if(peek(dut.io.debug.heapifierWrite).toInt == 1){
        println("INTERNAL WRITE!!!!!!!!!!!!!!!!!")
      }
      // catch writes
      if (peek(dut.io.ramWritePort.write) == 1) {
        println("WRITE!!!!!!!!!!!!!!!!!!!!!!!")
        for (i <- 0 until childrenCount) {
          Mem(lastWriteAddr)(i)(0) = peek(dut.io.ramWritePort.data(i).cycl).toInt
          Mem(lastWriteAddr)(i)(1) = peek(dut.io.ramWritePort.data(i).norm).toInt
        }
      }
      step(1)
      // print states
      if (debugOutput) {
        println(s"States: ${peek(dut.io.debug.state)} || ${peek(dut.io.debug.heapifierState)} at index ${peek(dut.io.debug.heapifierIndex)}\nMemory:\n${peek(dut.io.head.prio).mkString(":")}\n${Mem.map(_.map(_.mkString(":")).mkString(", ")).mkString("\n")}")
      }

      // simulate synchronous memory
      lastReadAddr = peek(dut.io.ramReadPort.address).toInt
      lastWriteAddr = peek(dut.io.ramWritePort.address).toInt
      iterations += 1
    }

  }
}

class PriorityQueueTests extends FlatSpec with Matchers {
  val normalWidth = 8
  val cyclicWidth = 2
  val heapSize = 17
  val childrenCount = 4
  val referenceWidth = 2
  val debugOutput = true
  "HeapPrioQ" should "pass" in {
    chisel3.iotesters.Driver(() => new HeapPrioQ(heapSize,childrenCount,normalWidth,cyclicWidth,referenceWidth)) {
      c => new Test1(c,normalWidth,cyclicWidth,heapSize,childrenCount,referenceWidth,debugOutput)
    } should be(true)
  }
}