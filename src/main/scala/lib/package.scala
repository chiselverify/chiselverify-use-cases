import chisel3._
import chisel3.util._

package object lib {
  class Priority(val nWid: Int, val cWid: Int) extends Bundle{
    val norm = UInt(nWid.W)
    val cycl = UInt(cWid.W)
    override def cloneType = new Priority(nWid, cWid).asInstanceOf[this.type]
  }
  class PriorityAndID(val nWid: Int, val cWid: Int, val rWid: Int) extends Bundle {
    val prio = new Priority(nWid, cWid)
    val id = UInt(rWid.W)
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
