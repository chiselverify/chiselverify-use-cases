/*import chisel3.iotesters.PeekPokeTester
import org.scalatest._
import old.MinimumFinder


class MinimumFinderTester(dut: MinimumFinder, n: Int, w: Int, testPasses: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {
  /////////////////////////////////// helper methods ///////////////////////////////////
  def populatedList(): Array[BigInt] = {
    val rand = scala.util.Random
    return Array.fill(n){rand.nextInt(math.pow(2,w).toInt)}
  }
  def applyVec(list: Array[BigInt]) = {
    for(i <- 0 until n){
      poke(dut.io.values(i).norm,list(i))
    }
  }
  def calculateOut(list: Array[BigInt]): BigInt = {
    val mins = list.zipWithIndex.filter(_._1 == list.min).map(_._2)
    var out: BigInt = 0
    for(i <- mins){
      out += 1 << i
    }
    return out
  }
  /////////////////////////////////// helper methods ///////////////////////////////////
  /////////////////////////////////// Test ///////////////////////////////////
  for(i <- 0 until testPasses){
    val values = populatedList()
    applyVec(values)
    if(debugOutput) println("\n"+values.mkString(", ")+"\n"+peek(dut.io.idx).toInt.toBinaryString.reverse.split("").mkString(", "))
    expect(dut.io.idx,calculateOut(values))
  }
  /////////////////////////////////// Test ///////////////////////////////////
}

class MinimumFinderTest extends FlatSpec with Matchers {
  val n = 30 // note that simulation will not work for n>30 due to some datatype max value issues
  val w = 8
  val testPasses = 100
  val debugOutput = false
  "old.MinimumFinder" should "identify all minimum values" in {
    chisel3.iotesters.Driver(() => new MininumFinder(n,w)) { c => new MinimumFinderTester(c,n,w,testPasses,debugOutput) } should be(true)
  }
}*/