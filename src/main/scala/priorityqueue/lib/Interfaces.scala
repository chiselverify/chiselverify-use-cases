package priorityqueue.lib

import chisel3.util.log2Ceil
import chisel3._
import priorityqueue.lib.BundleTypes.PriorityID
import priorityqueue.lib.Parameters.{addrWid, childrenCnt, memSize, rWid}

/**
 * contains relevant bundle types and port types for the heap-based priority queue
 */

object Interfaces {

  class rdPort extends Bundle { // as seen from reader side
    val address = Output(UInt(addrWid.W))
    val data = Input(Vec(childrenCnt, new PriorityID))

    override def cloneType = (new rdPort).asInstanceOf[this.type]
  }

  class wrPort extends Bundle { // as seen from writer side
    val address = Output(UInt(addrWid.W))
    val mask = Output(UInt(childrenCnt.W))
    val data = Output(Vec(childrenCnt, new PriorityID))
    val write = Output(Bool())

    override def cloneType = (new wrPort).asInstanceOf[this.type]
  }

  class searchPort extends Bundle { // as seen from requester side
    val refID = Output(UInt(rWid.W))
    val heapSize = Output(UInt(log2Ceil(memSize + 1).W))
    val res = Input(UInt(log2Ceil(memSize).W))
    val search = Output(Bool())
    val error = Input(Bool())
    val done = Input(Bool())

    override def cloneType = (new searchPort).asInstanceOf[this.type]
  }

}
