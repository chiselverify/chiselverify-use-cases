import chisel3._
import chisel3.util._

class HeapPrioQ(
                 size : Int, // max number of elements in queue
                 childrenCount : Int, // Number of children per node. Must be 2^m
                 normalPriorityWidth : Int, // Width of normal priority
                 cyclicPriorityWidth : Int, // Width of cyclic priority
                 referenceWidth : Int // Width of reference. Must be >= clog2(SIZE)
                  ) extends Module{
  val io = IO(new Bundle{
    // =====================================================
    // Interface for signaling head element to user.
    // I.e. the element with the lowest priority
    // -----------------------------------------------------
    val head = new Bundle{
      val valid = Output(Bool())
      val none = Output(Bool())
      val prio = Output(UInt((normalPriorityWidth+cyclicPriorityWidth).W))
      val refID = Output(UInt(referenceWidth.W))
    }
    // =====================================================
    // Interface for element insertion/removal
    // Timing:
    // User must maintain input asserted until done is asserted.
    // User must deassert input when done is asserted (unless a new cmd is made).
    // User must ensure that reference is unique.
    // -----------------------------------------------------
    val cmd = new Bundle{
      // inputs
      val valid = Input(Bool())
      val op = Input(Bool()) // 0=Remove, 1=Insert
      val prio = Input(UInt((normalPriorityWidth+cyclicPriorityWidth).W))
      val refID = Input(UInt(referenceWidth.W))
      // outputs
      val done = Output(Bool())
      val result = Output(Bool()) // 0=Success, 1=Failure
      val rm_prio = Output(UInt((normalPriorityWidth+cyclicPriorityWidth).W))
    }

  })
  if(!isPow2(childrenCount)) throw new Exception("The number of children must be a power of 2!")
}
