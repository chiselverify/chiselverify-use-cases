package priorityqueue.modules

import chisel3._
import chisel3.util._
import priorityqueue.lib.BundleTypes._
import priorityqueue.lib.Parameters._

/**
  * Determines the highest priority (smallest number) with the lowest index among the input values.
  * Outputs both the value and index
  *
  * @param n    number of priorities to compare
  * @param nWid width of the normal priority field
  * @param cWid width of the cyclic priority field
  * @param rWid width of the reference ID
  */
class MinFinder(n: Int) extends Module {
  val io = IO(new Bundle {
    val values = Input(Vec(n, new PriorityID))
    val currentSC = Input(UInt(cWid.W))
    val res = Output(new PriorityID)
    val idx = Output(UInt(log2Ceil(n).W))
  })

  class Dup extends Bundle {
    val v = new PriorityID
    val normalizedC = UInt(cWid.W)
    val isParent = Bool()
    val idx = UInt(log2Ceil(n).W)
  }

  // bundle input values with their corresponding index
  val inDup = Wire(Vec(n, new Dup()))
  for (i <- 0 until n) {
    inDup(i).v := io.values(i)
    inDup(i).normalizedC := io.values(i).prio.c - io.currentSC
    inDup(i).idx := i.U
    inDup(i).isParent := (i==0).B
  }

  // create a reduced tree structure to find the minimum value
  // lowest cyclic priority wins
  // if cyclic priorities are equal the normal priority decides
  // if both are equal the index decides
  val res = inDup.reduceTree((x: Dup, y: Dup) => {
    val out = Wire(new Dup)
    when(y.v.active && x.v.active) {

      when(y.normalizedC < x.normalizedC) {
        out := y
      }.elsewhen(y.normalizedC === x.normalizedC) {

        when(y.v.prio.n < x.v.prio.n) {
          out := y
        }.elsewhen(y.v.prio.n === x.v.prio.n) {

          when(y.isParent) {
            out := y
          }.otherwise {
            out := x
          }

        }.otherwise {
          out := x
        }

      }.otherwise {
        out := x
      }

    }.elsewhen(y.v.active){
      out := y
    }.otherwise{
      out := x
    }
    out
  })

  io.res := res.v
  io.idx := res.idx
}
