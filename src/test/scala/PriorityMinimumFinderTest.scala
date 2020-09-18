import chisel3.iotesters.PeekPokeTester
import org.scalatest._

class PriorityMinimumFinderTester(dut: PriorityMinimumFinder, NORMAL_WID: Int, CYCLIC_WID: Int, SIZE: Int, testPasses: Int, debugOutput: Boolean) extends PeekPokeTester(dut) {
  /////////////////////////////////// helper methods ///////////////////////////////////
  def populatedList(): Array[Array[BigInt]] = {
    val rand = scala.util.Random
    return Array.fill(SIZE){Array(rand.nextInt(math.pow(2,CYCLIC_WID).toInt),rand.nextInt(math.pow(2,NORMAL_WID).toInt))}
  }
  def applyVec(list: Array[Array[BigInt]]) = {
    for(i <- 0 until SIZE){
      poke(dut.io.values(i),(list(i)(0)<<NORMAL_WID)+list(i)(1))
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
    if(debugOutput) println("\n"+values.map(_(0)).mkString(", ")+"\n"+values.map(_(1)).mkString(", ")++"\n"+peek(dut.io.out).toString())
    expect(dut.io.out,calculateOut(values))
  }
  /////////////////////////////////// Test ///////////////////////////////////
}

class PriorityMinimumFinderTest extends FlatSpec with Matchers {
  val normalWidth = 25
  val cyclicWidth = 2
  val numberOfValues = 8 // note that simulation will not work for >30 due to some datatype max value issues
  val testPasses = 100
  val debugOutput = false
  "PriorityMinimumFinder" should "identify minimum value with the lowest index" in {
    chisel3.iotesters.Driver(() => new PriorityMinimumFinder(numberOfValues, normalWidth, cyclicWidth)) {
      c => new PriorityMinimumFinderTester(c,normalWidth,cyclicWidth,numberOfValues,testPasses,debugOutput)
    } should be(true)
  }
}