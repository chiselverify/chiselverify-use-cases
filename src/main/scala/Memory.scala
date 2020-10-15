import chisel3._
import chisel3.util._
import lib._

class Memory(size: Int, chCount: Int, rWid: Int, data: PriorityAndID) extends MultiIOModule {
  val rd = IO(Flipped(new rdPort(log2Ceil(size/chCount), Vec(chCount, data))))
  val wr = IO(Flipped(new wrPort(log2Ceil(size/chCount), chCount, Vec(chCount, data))))
  val srch = IO(Flipped(new searchPort(size,rWid)))

  // create memory
  val mems = Seq.fill(1)(SyncReadMem((size/chCount)/1, Vec(chCount, data)))

  // read port
  val rdAddr = Wire(UInt(log2Ceil((size/chCount)/1).W))
  val rdPorts = Seq.tabulate(1)(i => mems(i).read(rdAddr)) //TODO: if multiple mems need to chop address
  val rdData = Wire(Vec(chCount, data))
  rdData := rdPorts(0)
  rdAddr := rd.address
  when(true.B){ // TODO: multiplex between mem blocks here
    rdData := rdPorts(0)
  }
  rd.data := rdData

  // write port
  when(wr.write){
    Seq.tabulate(1)(i => mems(i).write(wr.address,wr.data,wr.mask.asBools))
  }

  val lastAddr = ((srch.heapSize - 1.U) >> log2Ceil(chCount)).asUInt

  // search state machine
  val idle :: search :: Nil = Enum(2)
  val state = RegInit(idle)
  val pointerReg = RegInit(0.U(log2Ceil(size/chCount)))
  val resVec = Wire(Vec(chCount, Bool()))
  val errorFlag = RegInit(false.B)

  resVec := rdData.map(_.id === srch.refID)

  srch.done := true.B
  srch.res := pointerReg
  srch.error := errorFlag

  switch(state){
    is(idle){
      state := idle
      when(srch.search){
        pointerReg := 0.U
        rdAddr := 0.U
        state := search
      }
    }
    is(search){
      errorFlag := false.B
      srch.done := false.B
      rdAddr := pointerReg
      pointerReg := pointerReg + 1.U
      state := search
      when(resVec.asUInt =/= 0.U || !srch.search){ //beaware: deasserting "search" aborts search
        state := idle
        pointerReg := (pointerReg << log2Ceil(chCount).U).asUInt + 1.U + OHToUInt(resVec)
      }.elsewhen(pointerReg > lastAddr){
        errorFlag := true.B
        state := idle
      }
    }
  }
}
