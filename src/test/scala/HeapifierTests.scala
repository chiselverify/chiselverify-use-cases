import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util._
import org.scalatest.{FlatSpec, Matchers}

private class RamTest(dut: Heapifier, normalWidth: Int, cyclicWidth: Int, heapSize: Int, childrenCount: Int, testPasses: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {
  val rand = scala.util.Random
  val Mem = Array.fill(heapSize/childrenCount)(Array.fill(childrenCount)(rand.nextInt(math.pow(2,normalWidth+cyclicWidth).toInt)))
  var lastReadAddr = 0
  var lastWriteAddr = 0
  val index = 40
  poke(dut.io.index, index)
  poke(dut.io.control.heapifyDown,false)
  poke(dut.io.control.heapifyUp,true)
  println("parent index and children row: "+((index-1)>>log2Ceil(childrenCount))+" is "+Mem.flatten.apply(((index-1)>>log2Ceil(childrenCount)))+" with children "+Mem.apply(((index-1)>>log2Ceil(childrenCount))).mkString(","))
  for(test <- 0 until testPasses){
    for(i <- 0 until childrenCount){
      poke(dut.io.ramReadPort.data(i),Mem(lastReadAddr)(i))
    }
    if(peek(dut.io.ramWritePort.write)==1){
      for(i <- 0 until childrenCount){
        Mem(lastWriteAddr)(i) = peek(dut.io.ramWritePort.data(i)).toInt
      }
    }

    println("\nstate: "+peek(dut.io.state)+
      "\nread at: "+lastReadAddr+
      (if(peek(dut.io.ramWritePort.write).toInt==1){"\nwrite "+peek(dut.io.ramWritePort.data).mkString(",")+" at: "+lastWriteAddr} else "")+
      "\nout: "+peek(dut.io.out)+" || swap: "+peek(dut.io.swap)+
    "\n"+peek(dut.io.minInputs).mkString(",")+
    "\nparent offset: "+peek(dut.io.parentOff))
    Mem.foreach(c => println(c.mkString(", ")))
    println("----------------------")
    lastReadAddr = peek(dut.io.ramReadPort.address).toInt
    lastWriteAddr = peek(dut.io.ramWritePort.address).toInt
    step(1)
    if(peek(dut.io.control.done).toInt == 1){
      poke(dut.io.control.heapifyUp,0)
    }
  }
}

private class AddressingTest(dut: Heapifier, normalWidth: Int, cyclicWidth: Int, heapSize: Int, childrenCount: Int, testPasses: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {
  poke(dut.io.ramReadPort.data, IndexedSeq.fill(childrenCount)(BigInt(0)))
  poke(dut.io.control.heapifyUp,true)
  poke(dut.io.control.heapifyDown,false)
  val rand = scala.util.Random
  for(i <- 0 until testPasses){
    val index = rand.nextInt(heapSize-1)
    poke(dut.io.index, index)
    expect(dut.io.ramReadPort.address,(((index-1)>>log2Ceil(childrenCount))-1)>>log2Ceil(childrenCount))
    step(1)
    expect(dut.io.ramReadPort.address,(index-1)>>log2Ceil(childrenCount))
    step(1)
    expect(dut.io.control.done, 1)
    step(1)
  }
}

class HeapifierTest extends FlatSpec with Matchers {
  val normalWidth = 8
  val cyclicWidth = 2
  val heapSize = 48
  val childrenCount = 4
  val testPasses = 9
  val debugOutput = true
  "Heapifier" should "pass" in {
    chisel3.iotesters.Driver(() => new Heapifier(heapSize,childrenCount,normalWidth,cyclicWidth)) {
      c => new RamTest(c,normalWidth,cyclicWidth,heapSize,childrenCount,testPasses,debugOutput)
    } should be(true)
  }
}