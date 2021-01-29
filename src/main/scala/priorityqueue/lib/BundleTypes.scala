package priorityqueue.lib

import chisel3._
import Parameters._

object BundleTypes {
  class Priority extends Bundle {
    val c = UInt(cWid.W)
    val n = UInt(nWid.W)

    override def cloneType = (new Priority).asInstanceOf[this.type]
  }

  class PriorityID extends Bundle {
    val active = Bool()
    val prio = new Priority
    val id = UInt(rWid.W)

    override def cloneType = (new PriorityID).asInstanceOf[this.type]
  }
}
