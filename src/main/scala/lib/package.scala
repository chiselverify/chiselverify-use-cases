import chisel3._
import chisel3.util._

package object lib {
  class PriorityBundle(val normalPriorityWidth: Int, val cyclicPriorityWidth: Int) extends Bundle{
    val norm = UInt(normalPriorityWidth.W)
    val cycl = UInt(cyclicPriorityWidth.W)
  }
}
