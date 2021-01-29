package priorityqueue

import chisel3._
import chisel3.util._
import priorityqueue.lib.BundleTypes._
import priorityqueue.modules.QueueControl
import priorityqueue.modules.{QueueControl, linearSearchMem}
import lib.Parameters._

/**
  * @param size    the maximum size of the heap
  */
class PriorityQueue(size: Int, superCycleCnt: Int, cycleCnt: Int, childrenCnt: Int, exposeState: Boolean = false) extends Module {
  lib.Parameters.setParameters(size, superCycleCnt, cycleCnt, childrenCnt)
  val io = IO(new Bundle {

    // Interface for signaling head element to user.
    // I.e. the element with the lowest priority
    val head = new Bundle {
      val valid = Output(Bool())
      val none = Output(Bool())
      val prio = Output(new Priority)
      val refID = Output(UInt(rWid.W))
    }

    // Interface for element insertion/removal
    // Timing:
    // User must maintain input asserted until done is asserted.
    // User must deassert input when done is asserted (unless a new cmd is made).
    // User must ensure that reference ID tags are unique.
    val cmd = new Bundle {
      // inputs
      val valid = Input(Bool())
      val op = Input(Bool()) // 0=Remove, 1=Insert
      val prio = Input(new Priority)
      val refID = Input(UInt(rWid.W))
      // outputs
      val done = Output(Bool())
      val result = Output(Bool()) // 0=Success, 1=Failure
      val rm_prio = Output(new Priority)
    }

    val state = if (exposeState) Some(Output(UInt())) else None

  })

  require(isPow2(childrenCnt), "The number of children per node needs to be a power of 2!")

  val mem = Module(new linearSearchMem)
  val queue = Module(new QueueControl)

  mem.srch <> queue.io.srch
  mem.rd <> queue.io.rdPort
  mem.wr <> queue.io.wrPort
  io.head <> queue.io.head
  io.cmd <> queue.io.cmd

  io.cmd.done := mem.srch.done && queue.io.cmd.done

  if (exposeState) io.state.get := queue.io.state

}
