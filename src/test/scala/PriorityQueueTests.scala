import Behavioural.heapifyUp
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

private class Test1(dut: HeapPrioQ, normalWidth: Int, cyclicWidth: Int, heapSize: Int, childrenCount: Int, referenceWidth: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {

  def findSmallest(arr: Seq[Array[Int]], normalWid: Int, cyclicWid: Int) : Array[Int] = {
    var smallest = arr.head
    for(item <- arr){
      if(item(0)<smallest(0) || (item(0)==smallest(0) && item(1)<smallest(1))){
        smallest = item
      }
    }
    return smallest
  }

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
  val toBeInserted = Seq.fill(heapSize)(Array(rand.nextInt(Math.pow(2,cyclicWidth).toInt), rand.nextInt(Math.pow(2,normalWidth).toInt)))
  // loop variables
  for(i <- 0 until heapSize) {
    var iterations = 0
    poke(dut.io.cmd.prio.cycl, toBeInserted(i)(0))
    poke(dut.io.cmd.prio.norm, toBeInserted(i)(1))
    poke(dut.io.cmd.valid, true)

    while (peek(dut.io.cmd.done).toInt == 0 || iterations < 1) {

      try {
        for (i <- 0 until childrenCount) {
          // ignores reads outside of array
          poke(dut.io.ramReadPort.data(i).cycl, Mem(lastReadAddr)(i)(0))
          poke(dut.io.ramReadPort.data(i).norm, Mem(lastReadAddr)(i)(1))
        }
      } catch {
        case e: IndexOutOfBoundsException => {}
      }
      // catch writes
      if (peek(dut.io.ramWritePort.write) == 1) {
        for (i <- 0 until childrenCount) {
          Mem(lastWriteAddr)(i)(0) = peek(dut.io.ramWritePort.data(i).cycl).toInt
          Mem(lastWriteAddr)(i)(1) = peek(dut.io.ramWritePort.data(i).norm).toInt
        }
      }
      // simulate synchronous memory
      lastReadAddr = peek(dut.io.ramReadPort.address).toInt
      lastWriteAddr = peek(dut.io.ramWritePort.address).toInt
      step(1)
      // print states
      if (debugOutput) {
        println(s"States: ${peek(dut.io.debug.state)} || ${peek(dut.io.debug.heapifierState)} at index ${peek(dut.io.debug.heapifierIndex)}\n"+
          s"Memory:\n${peek(dut.io.head.prio).mkString(":")}\n${Mem.map(_.map(_.mkString(":")).mkString(", ")).mkString("\n")}")
      }
      iterations += 1
    }
  }
  println(s"Inserted ${toBeInserted.map(_.mkString(":")).mkString(", ")}")
  println(s"Head of queue is ${peek(dut.io.head.prio).mkString(", ")}")
  val expected = findSmallest(toBeInserted,normalWidth,cyclicWidth)
  expect(dut.io.head.prio.norm, expected(1))
  expect(dut.io.head.prio.cycl, expected(0))
}

class PriorityQueueTests extends FlatSpec with Matchers {
  val normalWidth = 8
  val cyclicWidth = 2
  val heapSize = 17
  val childrenCount = 4
  val referenceWidth = 2
  val debugOutput = false
  "HeapPrioQ" should "pass" in {
    chisel3.iotesters.Driver(() => new HeapPrioQ(heapSize,childrenCount,normalWidth,cyclicWidth,referenceWidth)) {
      c => new Test1(c,normalWidth,cyclicWidth,heapSize,childrenCount,referenceWidth,debugOutput)
    } should be(true)
  }
}