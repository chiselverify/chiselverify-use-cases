import chisel3._
import chisel3.util._
import lib._

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
      val prio = Output(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))
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
      val valid = Input(Bool()) //TODO: when is head valid?
      val op = Input(Bool()) // 0=Remove, 1=Insert
      val prio = Input(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))
      val refID = Input(UInt(referenceWidth.W))
      // outputs
      val done = Output(Bool())
      val result = Output(Bool()) // 0=Success, 1=Failure
      val rm_prio = Output(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))
    }

    val ramReadPort = new ramReadPort(log2Ceil(size/childrenCount),Vec(childrenCount,new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
    val ramWritePort = new ramWritePort(log2Ceil(size/childrenCount),Vec(childrenCount,new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))

    val debug = new Bundle{
      val state = Output(UInt())
      val heapifierState = Output(UInt())
      val heapifierIndex = Output(UInt(log2Ceil(size).W))
      val heapifierWrite = Output(Bool())
    }
  })
  if(!isPow2(childrenCount)) throw new Exception("The number of children must be a power of 2!")

  // modules
  val heapifier = Module(new Heapifier(size, childrenCount, normalPriorityWidth, cyclicPriorityWidth))

  // state elements
  val idle :: headInsertion:: insertion0 :: insertion1 :: insertion2 :: waitForHeapifyUp :: Nil = Enum(6)
  val stateReg = RegInit(idle)
  val heapSizeReg = RegInit(0.U(log2Ceil(size+1).W))
  val headReg = RegInit(0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
  val tempReg = RegInit(VecInit(Seq.fill(childrenCount)(0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))))

  val index = WireDefault(0.U(log2Ceil(size).W))
  val indexToRam = ((index - 1.U) >> log2Ceil(childrenCount)).asUInt
  val indexOffset = Mux(index === 0.U, 0.U, index(log2Ceil(childrenCount),0) - 1.U(log2Ceil(size).W))

  heapifier.io.control.heapifyUp := false.B
  heapifier.io.control.heapifyDown := false.B
  heapifier.io.control.index := heapSizeReg
  heapifier.io.control.heapSize := heapSizeReg
  heapifier.io.headPort.rdData := headReg
  when(heapifier.io.headPort.write){
    headReg := heapifier.io.headPort.wrData
  }
  io.ramReadPort.address := heapifier.io.ramReadPort.address
  heapifier.io.ramReadPort.data := io.ramReadPort.data
  io.ramWritePort.address := heapifier.io.ramWritePort.address
  io.ramWritePort.data := heapifier.io.ramWritePort.data
  io.ramWritePort.write := heapifier.io.ramWritePort.write

  io.head.prio := headReg
  io.head.none := heapSizeReg === 0.U
  io.head.valid := true.B
  io.head.refID := 0.U
  io.cmd.done := false.B
  io.cmd.result := false.B
  io.cmd.rm_prio := 0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))

  //TODO: remove debug outputs
  io.debug.state := stateReg
  io.debug.heapifierState := heapifier.io.state
  io.debug.heapifierIndex := heapifier.io.indexOut
  io.debug.heapifierWrite := heapifier.io.ramWritePort.write


  switch(stateReg){
    is(idle){
      io.cmd.done := true.B
      when(io.cmd.valid){
         when(io.cmd.op){
           stateReg := insertion0
           when(heapSizeReg === 0.U){
             stateReg := headInsertion
           }
         }.otherwise{
           stateReg := idle
         }
      }
    }
    is(headInsertion){
      headReg := io.cmd.prio
      io.head.valid := false.B
      heapSizeReg := heapSizeReg + 1.U
      stateReg := idle
    }
    is(insertion0){
      index := heapSizeReg
      io.ramReadPort.address := indexToRam
      stateReg := insertion1
    }
    is(insertion1){
      index := heapSizeReg
      io.ramWritePort.address := indexToRam
      tempReg := io.ramReadPort.data
      stateReg := insertion2
    }
    is(insertion2){
      index := heapSizeReg
      io.ramWritePort.data := tempReg
      io.ramWritePort.data(indexOffset) := io.cmd.prio
      io.ramWritePort.write := true.B
      heapSizeReg := heapSizeReg + 1.U
      heapifier.io.control.index := Mux(heapSizeReg < childrenCount.U, 0.U, ((heapSizeReg - 1.U) << log2Ceil(childrenCount)).asUInt)
      heapifier.io.control.heapifyUp := true.B
      stateReg := waitForHeapifyUp
    }
    is(waitForHeapifyUp){
      heapifier.io.control.heapifyUp := true.B
      stateReg := waitForHeapifyUp
      io.ramWritePort.write := heapifier.io.ramWritePort.write
      when(heapifier.io.control.done){
        stateReg := idle
      }
    }
  }
}
