import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}
import Behavioural._

private class HeapifyUpTest(dut: Heapifier, normalWidth: Int, cyclicWidth: Int, heapSize: Int, childrenCount: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {

  // setup random memory state
  val rand = scala.util.Random
  val priorities: Array[Array[Int]] = Array.fill(heapSize)(Array(rand.nextInt(math.pow(2,cyclicWidth).toInt),rand.nextInt(math.pow(2,normalWidth).toInt)))
  val prioritiesOriginal = priorities.map(_.clone())
  // create 3d array. First index is RAM address, 2nd index selects element at RAM address, 3rd selects cyclic or normal priority value
  val Mem: Array[Array[Array[Int]]] = priorities.slice(1,priorities.length).sliding(childrenCount,childrenCount).toArray.map(_.map(_.clone()))
  // root/head element is not a part of RAM
  val root = priorities(0).clone()

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
  poke(dut.io.control.index, index)
  poke(dut.io.control.heapifyDown,false)
  poke(dut.io.control.heapifyUp,true)
  poke(dut.io.headPort.rdData.cycl,root(0))
  poke(dut.io.headPort.rdData.norm,root(1))
  poke(dut.io.control.heapSize,heapSize)

  // loop variables
  var iterations = 0
  while(peek(dut.io.state).toInt!=0 || iterations < 2){

    for(i <- 0 until childrenCount){
      // ignores reads outside of array
      try {
        poke(dut.io.ramReadPort.data(i).cycl, Mem(lastReadAddr)(i)(0))
        poke(dut.io.ramReadPort.data(i).norm, Mem(lastReadAddr)(i)(1))
      }catch{
        case e: IndexOutOfBoundsException => {}
      }
    }
    // catch writes
    if(peek(dut.io.ramWritePort.write)==1){
      for(i <- 0 until childrenCount){
        Mem(lastWriteAddr)(i)(0) = peek(dut.io.ramWritePort.data(i).cycl).toInt
        Mem(lastWriteAddr)(i)(1) = peek(dut.io.ramWritePort.data(i).norm).toInt
      }
    }
    // catch writes to head element
    if(peek(dut.io.headPort.write).toInt == 1){
      root(0) = peek(dut.io.headPort.wrData.cycl).toInt
      root(1) = peek(dut.io.headPort.wrData.norm).toInt
    }
    // print states
    if(debugOutput){
      println(s"\nstate: ${peek(dut.io.state)} | index: ${peek(dut.io.indexOut)} | nextIndex: ${peek(dut.io.nextIndexOut)}\n"+
      s"ReadPort: ${peek(dut.io.ramReadPort.address)} | ${Mem.apply(lastReadAddr).map(_.mkString(":")).mkString(",")}\n"+
      s"WritePort: ${peek(dut.io.ramWritePort.address)} | ${peek(dut.io.ramWritePort.data).sliding(2,2).map(_.mkString(":")).mkString(",")} | ${peek(dut.io.ramWritePort.write)}\n"+
      s"MinInput: ${peek(dut.io.minInputs).sliding(2,2).map(_.mkString(":")).mkString(", ")}\n"+
      s"parentOffset: ${peek(dut.io.parentOff)}\n"+
      s"Memory:\n${root.mkString(":")}\n${Mem.map(_.map(_.mkString(":")).mkString(", ")).mkString("\n")}")
    }

    // simulate synchronous memory
    lastReadAddr = peek(dut.io.ramReadPort.address).toInt
    lastWriteAddr = peek(dut.io.ramWritePort.address).toInt

    // catch components done signal
    if(peek(dut.io.control.done).toInt == 1 && iterations > 2){
      poke(dut.io.control.heapifyUp,0)
      if(prioritiesOriginal.slice(1,prioritiesOriginal.length).deep != Mem.flatten.deep){
        expect(dut.io.control.swapped, true) // should indicate that a swap has occurred
      }

    }
    step(1)
    iterations +=1
  }
  // print out components and models results
  println(s"\nStart from index $index:\n${prioritiesOriginal.map(_.mkString(":")).mkString(", ")}\nResult:\n${root.mkString(":")}, ${Mem.flatten.map(_.mkString(":")).mkString(", ")}\nModel:\n${priorities.map(_.mkString(":")).mkString(", ")}")
  // check for equality
  expect(priorities(0).deep == root.deep, "")
  expect(priorities.slice(1,priorities.length).deep == Mem.flatten.deep,"")
}

private class HeapifyDownTest(dut: Heapifier, normalWidth: Int, cyclicWidth: Int, heapSize: Int, childrenCount: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {

  // setup random memory state
  val rand = scala.util.Random
  val priorities: Array[Array[Int]] = Array.fill(heapSize)(Array(rand.nextInt(math.pow(2,cyclicWidth).toInt),rand.nextInt(math.pow(2,normalWidth).toInt)))
  val prioritiesOriginal = priorities.map(_.clone())
  // create 3d array. First index is RAM address, 2nd index selects element at RAM address, 3rd selects cyclic or normal priority value
  val Mem: Array[Array[Array[Int]]] = priorities.slice(1,priorities.length).sliding(childrenCount,childrenCount).toArray.map(_.map(_.clone()))
  // root/head element is not a part of RAM
  val root = priorities(0).clone()

  // simulate synchronous memory
  var lastReadAddr = 0
  var lastWriteAddr = 0

  // determine the last index which has children
  var lastParent = Seq.tabulate(heapSize)(i => (i * childrenCount)+1 < heapSize).lastIndexOf(true)
  // randomly set starting index
  val index = rand.nextInt(lastParent)

  // apply behavioral model to memory state
  heapifyDown(priorities, childrenCount, heapSize, index)

  // setup inputs of dut
  poke(dut.io.control.index, index)
  poke(dut.io.control.heapifyDown,true)
  poke(dut.io.control.heapifyUp,false)
  poke(dut.io.headPort.rdData.cycl,root(0))
  poke(dut.io.headPort.rdData.norm,root(1))
  poke(dut.io.control.heapSize,heapSize)

  // loop variables
  var iterations = 0
  while(peek(dut.io.state).toInt!=0 || iterations < 2){

    for(i <- 0 until childrenCount){
      // ignores reads outside of array
      try {
        poke(dut.io.ramReadPort.data(i).cycl, Mem(lastReadAddr)(i)(0))
        poke(dut.io.ramReadPort.data(i).norm, Mem(lastReadAddr)(i)(1))
      }catch{
        case e: IndexOutOfBoundsException => {}
      }
    }
    // catch writes
    if(peek(dut.io.ramWritePort.write)==1){
      for(i <- 0 until childrenCount){
        Mem(lastWriteAddr)(i)(0) = peek(dut.io.ramWritePort.data(i).cycl).toInt
        Mem(lastWriteAddr)(i)(1) = peek(dut.io.ramWritePort.data(i).norm).toInt
      }
    }
    // catch writes to head element
    if(peek(dut.io.headPort.write).toInt == 1){
      root(0) = peek(dut.io.headPort.wrData.cycl).toInt
      root(1) = peek(dut.io.headPort.wrData.norm).toInt
    }
    // print states
    if(debugOutput){
      println(s"\nstate: ${peek(dut.io.state)} | index: ${peek(dut.io.indexOut)} | nextIndex: ${peek(dut.io.nextIndexOut)}\n"+
        s"ReadPort: ${peek(dut.io.ramReadPort.address)} | ${Mem.apply(lastReadAddr).map(_.mkString(":")).mkString(",")}\n"+
        s"WritePort: ${peek(dut.io.ramWritePort.address)} | ${peek(dut.io.ramWritePort.data).sliding(2,2).map(_.mkString(":")).mkString(",")} | ${peek(dut.io.ramWritePort.write)}\n"+
        s"MinInput: ${peek(dut.io.minInputs).sliding(2,2).map(_.mkString(":")).mkString(", ")}\n"+
        s"parentOffset: ${peek(dut.io.parentOff)}\n"+
        s"Memory:\n${root.mkString(":")}\n${Mem.map(_.map(_.mkString(":")).mkString(", ")).mkString("\n")}")
    }

    // simulate synchronous memory
    lastReadAddr = peek(dut.io.ramReadPort.address).toInt
    lastWriteAddr = peek(dut.io.ramWritePort.address).toInt

    // catch components done signal
    if(peek(dut.io.control.done).toInt == 1 && iterations > 2){
      poke(dut.io.control.heapifyUp,0)
      if(prioritiesOriginal.slice(1,prioritiesOriginal.length).deep != Mem.flatten.deep){
        expect(dut.io.control.swapped, true) // should indicate that a swap has occurred
      }
    }
    step(1)
    iterations +=1
  }
  // print out components and models results
  println(s"\nStart from index $index:\n${prioritiesOriginal.map(_.mkString(":")).mkString(", ")}\nResult:\n${root.mkString(":")}, ${Mem.flatten.map(_.mkString(":")).mkString(", ")}\nModel:\n${priorities.map(_.mkString(":")).mkString(", ")}")

  // check for equality
  expect(priorities(0).deep == root.deep, "")
  expect(priorities.slice(1,priorities.length).deep == Mem.flatten.deep,"")
}

class HeapifierTest extends FlatSpec with Matchers {
  val normalWidth = 8
  val cyclicWidth = 2
  val heapSize = 16
  val childrenCount = 4
  val debugOutput = false
  "Heapifier" should "heapify up" in {
    chisel3.iotesters.Driver(() => new Heapifier(heapSize,childrenCount,normalWidth,cyclicWidth)) {
      c => new HeapifyUpTest(c,normalWidth,cyclicWidth,heapSize,childrenCount,debugOutput)
    } should be(true)
  }
  "Heapifier" should "heapify down" in {
    chisel3.iotesters.Driver(() => new Heapifier(heapSize,childrenCount,normalWidth,cyclicWidth)) {
      c => new HeapifyDownTest(c,normalWidth,cyclicWidth,heapSize,childrenCount,debugOutput)
    } should be(true)
  }

}