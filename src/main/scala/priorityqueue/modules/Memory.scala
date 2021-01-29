package priorityqueue.modules

import chisel3._
import chisel3.util._
import priorityqueue.lib.Interfaces.{rdPort, searchPort, wrPort}
import priorityqueue.lib.BundleTypes.PriorityID
import priorityqueue.lib.Parameters._

/**
  * Abstract interface for a sequential memory
  *   - ZBT -> write address is as well supplied on clock cycle in advance of a write
  *   - CAM functionality -> when a reference ID is provided, the module should find the first occurrence in memory
  *   - should be initialized to all 1's
  */
abstract class SearchSeqMem extends MultiIOModule {
  val rd = IO(Flipped(new rdPort))
  val wr = IO(Flipped(new wrPort))
  val srch = IO(Flipped(new searchPort))
}

/**
  * A implementation of SearchSeqMem, where the CAM-functionality is provided by a linear search through the memory
  *
  * @param size    the number of values to be saved
  * @param childrenCnt values are grouped together by this factor
  * @param cWid    width of the cycic priority
  * @param nWid    width of the normal priority
  * @param rWid    width of the reference ID
  */
class linearSearchMem extends SearchSeqMem {

  // create memory
  val mem = SyncReadMem(size / childrenCnt, Vec(childrenCnt, new PriorityID))

  // read port
  val rdAddr = Wire(UInt(log2Ceil(size / childrenCnt).W))
  val rdPort = mem.read(rdAddr)
  val rdData = Wire(Vec(childrenCnt, new PriorityID))
  rdData := rdPort
  rdAddr := rd.address
  rd.data := rdData

  // write port
  val wrData = Wire(Vec(childrenCnt, new PriorityID))
  val wrAddr = Wire(UInt(log2Ceil(size / childrenCnt).W))
  val wrMask = Wire(Vec(childrenCnt, Bool()))
  val write = Wire(Bool())
  wrData := wr.data
  wrAddr := wr.address
  wrMask := wr.mask.asBools
  write := wr.write
  when(write) {
    mem.write(RegNext(wrAddr), wrData, wrMask)
  }

  val lastAddr = Mux(srch.heapSize === 0.U, 0.U, ((srch.heapSize - 1.U) >> log2Ceil(childrenCnt)).asUInt)

  // search state machine
  val idle :: search :: setup :: Nil = Enum(3)
  val stateReg = RegInit(setup)
  val pointerReg = RegInit(0.U(log2Ceil(size).W))
  val resVec = Wire(Vec(childrenCnt, Bool()))
  val errorFlag = RegInit(false.B)

  val resetCell = WireDefault(VecInit(Seq.fill(childrenCnt)(VecInit(Seq.fill(cWid + nWid + rWid)(true.B)).asUInt.asTypeOf(new PriorityID))))

  resVec := rdData.map(_.id === srch.refID)

  srch.done := true.B
  srch.res := pointerReg
  srch.error := errorFlag

  switch(stateReg) {
    is(setup) {
      srch.done := false.B
      stateReg := setup
      pointerReg := pointerReg + 1.U

      wrAddr := pointerReg
      wrData := resetCell
      wrMask := VecInit(Seq.fill(childrenCnt)(true.B))
      write := true.B

      when(pointerReg === ((size / childrenCnt) + 1).U) {
        stateReg := idle
      }
    }
    is(idle) {
      stateReg := idle
      when(srch.search) {
        pointerReg := 0.U
        rdAddr := 0.U
        errorFlag := false.B
        stateReg := search
      }
    }
    is(search) {
      errorFlag := false.B
      srch.done := false.B
      rdAddr := pointerReg + 1.U
      pointerReg := pointerReg + 1.U
      stateReg := search
      when(resVec.asUInt =/= 0.U || !srch.search) { // deasserting "search" aborts search
        stateReg := idle
        pointerReg := (pointerReg << log2Ceil(childrenCnt)).asUInt + OHToUInt(PriorityEncoderOH(resVec)) + 1.U
      }.elsewhen(pointerReg >= lastAddr) {
        errorFlag := true.B
        stateReg := idle
      }
    }
  }
}
