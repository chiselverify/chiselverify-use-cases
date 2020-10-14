import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}
import chisel3._
import lib._

class MemoryTester(dut: Memory) extends PeekPokeTester(dut){
  def pokePrio(port: lib.Priority, norm: Int, cycl: Int) = {
    poke(port, Map("norm"->BigInt(norm),"cycl"->BigInt(cycl)))
  }
  def pokePrioID(port: PriorityAndID, id: Int, cycl: Int, norm: Int) = {
    pokePrio(port.prio,norm,cycl)
    poke(port.id, BigInt(id))
  }
  def readMem(): String ={
    val mem = new Array[String](16)
    poke(dut.rd.address, 0)
    step(1)
    for(i <- 0 until 4){
      mem(i) = peek(dut.rd.data).sliding(3,3).sliding(4,4).toArray.map(_.map(_.mkString(":")).mkString(",")).mkString(", ")
      if(i<4) poke(dut.rd.address, i+2)
      step(1)
    }
    return mem.mkString(", ")
  }
  /*
  def initMem() = {
    poke(dut.io.write.index, 1)
    step(1)
    for(i <- 0 until 16){
      pokePrioID(dut.io.write.data, 0, 0, 0)
      poke(dut.io.write.write, 1)
      if(i<16) poke(dut.io.write.index, i+2)
      step(1)
    }
    poke(dut.io.write.write, 0)
  }*/

  poke(dut.rd.address, 0)
  poke(dut.wr.address, 0)
  poke(dut.wr.write, 0)
  pokePrioID(dut.wr.data(0), 0,0,0)
  pokePrioID(dut.wr.data(1), 0,0,0)
  pokePrioID(dut.wr.data(2), 0,0,0)
  pokePrioID(dut.wr.data(3), 0,0,0)
  poke(dut.srch.id, 0)
  poke(dut.srch.search, 0)
  step(5)
  poke(dut.wr.address, 0)
  poke(dut.wr.mask, 1)
  step(1)
  poke(dut.wr.address, 1)
  pokePrioID(dut.wr.data(1), 2,2,2)
  poke(dut.wr.write, 1)
  poke(dut.wr.mask, 1)
  step(2)
  println(readMem())

  /*
  step(5)
  println(readMem())
  initMem()
  println(readMem())
  step(5)
  poke(dut.io.write.index, 3)
  poke(dut.io.read.index, 3)
  step(1)
  pokePrioID(dut.io.write.data, 7, 1, 25)
  println(s"${peek(dut.io.wData).mkString(",")}")
  println(s"${peek(dut.io.bMask).toInt.toBinaryString}")
  poke(dut.io.write.write, 1)
  step(1)
  println(readMem())
  poke(dut.io.write.write,0)
  step(1)
  println(readMem())
  println(s"${peek(dut.io.read.data).mkString(",")}")
  poke(dut.io.write.index,2)
  pokePrioID(dut.io.write.data, 1, 2, 55)
  println(s"${peek(dut.io.wData).mkString(",")}")
  println(s"${peek(dut.io.bMask).toInt.toBinaryString}")
  poke(dut.io.write.write, 1)
  step(1)
  poke(dut.io.write.write,0)
  step(1)
  println(s"${peek(dut.io.read.data).mkString(",")}")
  poke(dut.io.read.mode, 1)
  println(s"${peek(dut.io.read.data).mkString(",")}")
  step(1)
  poke(dut.io.search.key, 7)
  poke(dut.io.search.search, 1)
  step(1)
  poke(dut.io.search.search,1)
  while(peek(dut.io.busy).toInt == 1) step(1)
  println(s"search res: ${peek(dut.io.search.res)}")
  println(readMem())
*/

}


class MemoryTest extends FlatSpec with Matchers {
  val normalWidth = 8
  val cyclicWidth = 2
  val heapSize = 17
  val childrenCount = 4
  val referenceWidth = 2
  val debugOutput = true
  "MaskedSeqMem" should "pass" in {
    chisel3.iotesters.Driver(() => new Memory(256,4,4,new PriorityAndID(8,2,4))) {
      c => new MemoryTester(c)
    } should be(true)
  }
}