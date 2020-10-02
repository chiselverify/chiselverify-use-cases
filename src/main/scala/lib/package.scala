import chisel3._
import chisel3.util._

package object lib {
  class PriorityBundle(val normalPriorityWidth: Int, val cyclicPriorityWidth: Int) extends Bundle{
    val norm = UInt(normalPriorityWidth.W)
    val cycl = UInt(cyclicPriorityWidth.W)
  }
  class ramReadPort[T <: Data](val addressWidth: Int, dType: T) extends Bundle{
    val address = Output(UInt(addressWidth.W))
    val data = Input(dType)
    override def cloneType = (new ramReadPort[T](addressWidth,dType)).asInstanceOf[this.type]
  }
  class ramWritePort[T <: Data](val addressWidth: Int, dType: T) extends Bundle{
    val address = Output(UInt(addressWidth.W))
    val data = Output(dType)
    val write = Output(Bool())
    override def cloneType = (new ramWritePort[T](addressWidth,dType)).asInstanceOf[this.type]
  }
}
