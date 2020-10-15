import chisel3.iotesters.PeekPokeTester
import org.scalatest._

class MinFinderTester(dut: MinFinder, normalPriorityWidth: Int, cyclicPriorityWidth: Int, n: Int, testPasses: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {
  /////////////////////////////////// helper methods ///////////////////////////////////
  def populatedList(): Array[Array[BigInt]] = {
    val rand = scala.util.Random
    return Array.fill(n){Array(rand.nextInt(math.pow(2,cyclicPriorityWidth).toInt),rand.nextInt(math.pow(2,normalPriorityWidth).toInt))}
  }
  def applyVec(list: Array[Array[BigInt]]) = {
    for(i <- 0 until n){
      poke(dut.io.values(i).prio.cycl,list(i)(0))
      poke(dut.io.values(i).prio.norm,list(i)(1))
    }
  }
  def calculateOut(list: Array[Array[BigInt]]): BigInt = {
    val cyclic = list.map(_(0))
    val cyclicMins = cyclic.zipWithIndex.filter(_._1 == cyclic.min).map(_._2)
    if(cyclicMins.length == 1){
      return cyclicMins(0)
    }else{
      val normals = list.map(_(1))
      val candidates = normals.filter(a => cyclicMins.contains(normals.indexOf(a)))
      val normalMins = candidates.zipWithIndex.filter(_._1 == candidates.min).map(_._2)
      return normals.indexOf(candidates(normalMins(0)))
    }
  }
  /////////////////////////////////// helper methods ///////////////////////////////////
  /////////////////////////////////// Test ///////////////////////////////////
  for(i <- 0 until testPasses){
    val values = populatedList()
    applyVec(values)
    if(debugOutput) println("\n"+values.map(_(0)).mkString(", ")+"\n"+values.map(_(1)).mkString(", ")++"\n"+peek(dut.io.idx).toString())
    expect(dut.io.idx,calculateOut(values))
  }
  /////////////////////////////////// Test ///////////////////////////////////
}

class MinFinderTest extends FlatSpec with Matchers {
  val normalPriorityWidth = 25
  val cyclicPriorityWidth = 2
  val numberOfValues = 8
  val testPasses = 500
  val debugOutput = false
  "old.PriorityMinimumFinder" should "identify minimum value with the lowest index" in {
    chisel3.iotesters.Driver(() => new MinFinder(numberOfValues, normalPriorityWidth, cyclicPriorityWidth, 3)) {
      c => new MinFinderTester(c,normalPriorityWidth,cyclicPriorityWidth,numberOfValues,testPasses,debugOutput)
    } should be(true)
  }
}