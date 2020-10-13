import chisel3._
import chisel3.util._
import lib._

//FIXME: same cell repeats over and over

class MaskedSeqMem(size: Int, pSize: Int, chCount: Int, nWid: Int, cWid: Int, rWid: Int) extends Module{
  val io = IO(new Bundle{
    val read = new Bundle{
      val index = Input(UInt(log2Ceil(size+1).W))
      val data = Output(Vec(chCount, new PriorityAndID(nWid, cWid, rWid)))
      val mode = Input(Bool())
    }
    val write = new Bundle{
      val index = Input(UInt(log2Ceil(size+1).W))
      val data = Input(new PriorityAndID(nWid, cWid, rWid))
      val write = Input(Bool())
    }
    val search = new Bundle{
      val key = Input(UInt(rWid.W))
      val search = Input(Bool())
      val res = Output(UInt(log2Ceil(size).W))
    }
    val busy = Output(Bool())
    val wData = Output(Vec(chCount, new PriorityAndID(nWid, cWid, rWid)))
    val bMask = Output(UInt(4.W))
  })

  val mem = SyncReadMem(size/chCount, Vec(chCount, new PriorityAndID(nWid, cWid, rWid)))

  // read port setup
  val rIndex = Wire(UInt(log2Ceil(size).W))
  rIndex := io.read.index
  val rAddr = Wire(UInt(log2Ceil(size/chCount).W))
  rAddr := ((rIndex - 1.U) << log2Ceil(chCount).U).asUInt
  val rOff = rIndex(log2Ceil(chCount),0) - 1.U(log2Ceil(size).W)
  val rPort = mem.read(rAddr)
  val rSingle = rPort(rOff)

  // write port setup
  val wIndex = Wire(UInt(log2Ceil(size).W))
  val wAddr = ((wIndex - 1.U) << log2Ceil(chCount).U).asUInt
  val wOff = wIndex(log2Ceil(chCount),0) - 1.U
  val wData = Wire(Vec(chCount, new PriorityAndID(nWid, cWid, rWid)))
  val write = WireDefault(false.B)
  val mask = RegInit(0.U(chCount.W))
  mask := UIntToOH(wOff)(chCount-1,0)
  when(write){
    mem.write(wAddr, wData, mask.asBools)
  }
  io.bMask := mask

  // hold all write entries low
  val zero = WireDefault(VecInit(Seq.fill(chCount)(0.U.asTypeOf(new PriorityAndID(nWid, cWid, rWid)))))
  wData := zero
  wIndex := io.write.index
  write := io.write.write
  wData(wOff) := io.write.data
  io.wData := wData

  io.read.data := rPort
  when(io.read.mode){
    io.read.data := zero
    io.read.data(0) := rSingle
  }


  val rw :: preSearch :: search :: Nil = Enum(3)
  val stateReg = RegInit(rw)


  val pointerReg = RegInit(0.U(log2Ceil(size).W))
  val resVec = WireInit(VecInit(Seq.fill(size/chCount)(false.B)))
  val resReg = RegInit(0.U(log2Ceil(size).W))
  io.search.res := resReg

  io.busy := false.B

  switch(stateReg){
    is(rw){
      io.busy := false.B
      pointerReg := 0.U

      stateReg := rw
      when(io.search.search){
        stateReg := search
      }
    }
    is(preSearch){
      io.busy := true.B
      rAddr := pointerReg
      stateReg := search
    }
    is(search){
      io.busy := true.B

      pointerReg := pointerReg + 1.U
      rAddr := pointerReg

      resVec := rPort.map(_.id === io.search.key)

      stateReg := search
      when(resVec.asUInt =/= 0.U){
        resReg := (pointerReg << log2Ceil(chCount).U).asUInt + 1.U + OHToUInt(resVec)
        stateReg := rw
      }
    }
  }

}
