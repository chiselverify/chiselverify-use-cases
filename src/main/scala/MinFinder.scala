import chisel3._
import chisel3.util._
import lib._

/**
 * Determines the smallest priority with the lowest index among the input values.
 * Outputs both the value and index
 * @param n number of priorities to compare
 * @param normalPriorityWidth width of the normal priority field
 * @param cyclicPriorityWidth width of the cyclic priority field
 */
class MinFinder(n: Int, normalPriorityWidth : Int, cyclicPriorityWidth : Int) extends Module{
  val io = IO(new Bundle{
    val values = Input(Vec(n, new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
    val res = Output(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))
    val idx = Output(UInt(log2Ceil(n).W))
  })

  class Dup extends Bundle {
    val v = new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)
    val idx = UInt(log2Ceil(n).W)
  }

  // bundle input values with their corresponding index
  val inDup = Wire(Vec(n, new Dup()))
  for (i <- 0 until n) {
    inDup(i).v := io.values(i)
    inDup(i).idx := i.U
  }

  // create a reduced tree structure to find the minimum value
  // lowest cyclic priority wins
  // if cyclic priorities are equal the normal priority decides
  val res = inDup.reduceTree((x: Dup, y: Dup) => Mux((x.v.cycl<y.v.cycl) || (x.v.cycl===y.v.cycl && (x.v.norm < y.v.norm || (x.v.norm===y.v.norm && x.idx<y.idx))),x,y))

  io.res := res.v
  io.idx := res.idx
}
