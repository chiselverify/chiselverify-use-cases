package old

import chisel3._
import chisel3.util._

/**
 * Determines the index of the smallest value in a set of values. The values represent
 * a priority composed of a cyclic priority and a normal priority. A smaller value
 * means a higher priority. Cyclic priority wins over normal priority. If there
 * are multiple elements of equally high priority, the one with the lowest
 * port index wins.
 * @param n the number of priorities to be compared
 * @param normalPriorityWidth the width of the normal priority
 * @param cyclicPriorityWidth the width of the cyclic piority
 */
class PriorityMinimumFinder(
                             n: Int,
                             normalPriorityWidth : Int,
                             cyclicPriorityWidth : Int
                            ) extends Module{
  val io = IO(new Bundle{
    val values = Input(Vec(n, UInt((normalPriorityWidth+cyclicPriorityWidth).W)))
    val out = Output(UInt(log2Ceil(n).W))
  })
  if(cyclicPriorityWidth == 0){
    val minimumFinder = Module(new MinimumFinder(n, normalPriorityWidth+cyclicPriorityWidth))
    minimumFinder.io.values := io.values
    io.out := PriorityEncoder(minimumFinder.io.out)
  }else{
    val cyclicPrio = io.values.map(_(normalPriorityWidth + cyclicPriorityWidth - 1,normalPriorityWidth))
    val cyclicMinimumFinder = Module(new MinimumFinder(n, cyclicPriorityWidth))
    cyclicMinimumFinder.io.values := cyclicPrio
    when((cyclicMinimumFinder.io.out & (cyclicMinimumFinder.io.out - 1.U)) === 0.U){ // check for competition
      io.out := PriorityEncoder(cyclicMinimumFinder.io.out)
    }.otherwise{
      val normalPrio = Seq.tabulate(n)(i => Mux(cyclicMinimumFinder.io.out(i),io.values(i)(normalPriorityWidth-1,0),((-1).S((normalPriorityWidth+cyclicPriorityWidth).W)).asUInt()))
      val normalMinimumFinder = Module(new MinimumFinder(n, normalPriorityWidth))
      normalMinimumFinder.io.values := normalPrio
      io.out := PriorityEncoder(normalMinimumFinder.io.out)
    }
  }
}