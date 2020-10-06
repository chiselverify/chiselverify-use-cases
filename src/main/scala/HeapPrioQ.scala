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
      val minOut = Output(UInt(log2Ceil(childrenCount).W))
      val minInputs = Output(Vec(childrenCount+1,new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
      val swap = Output(Bool())
    }
  })
  if(!isPow2(childrenCount)) throw new Exception("The number of children must be a power of 2!")

  // modules
  val heapifier = Module(new Heapifier(size, childrenCount, normalPriorityWidth, cyclicPriorityWidth))

  // state elements
  val idle :: headInsertion:: insertion0 :: insertion1 :: insertion2 :: waitForHeapifyUp :: lastRemoval :: removal0 :: removal1 :: removal2 :: removal3 :: waitForHeapifyDown :: headRemoval1 :: Nil = Enum(13)
  val stateReg = RegInit(idle)
  val heapSizeReg = RegInit(0.U(log2Ceil(size+1).W))
  val headReg = RegInit(0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
  val tempReg = RegInit(VecInit(Seq.fill(childrenCount)(0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))))

  val index = WireDefault(0.U(log2Ceil(size).W))
  val indexToRam = ((index - 1.U) >> log2Ceil(childrenCount)).asUInt
  val indexOffset = Mux(index === 0.U, 0.U, index(log2Ceil(childrenCount),0) - 1.U(log2Ceil(size).W))

  val incHeapsize = WireDefault(false.B)
  val decHeapsize = WireDefault(false.B)
  when(incHeapsize){
    heapSizeReg := heapSizeReg + 1.U
  }.elsewhen(decHeapsize){
    heapSizeReg := heapSizeReg - 1.U
  }

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

  // default assignments
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
  io.debug.minOut := heapifier.io.out
  io.debug.minInputs := heapifier.io.minInputs
  io.debug.swap := heapifier.io.swap


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
           stateReg := removal0
           when(io.cmd.refID === 0.U && heapSizeReg === 1.U){
             stateReg := lastRemoval
           }
         }
      }
    }
    is(headInsertion){
      headReg := io.cmd.prio
      io.head.valid := false.B
      incHeapsize := true.B
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
      incHeapsize := true.B
      heapifier.io.control.index := Mux(heapSizeReg < childrenCount.U, 0.U, ((heapSizeReg - 1.U) << log2Ceil(childrenCount)).asUInt)
      heapifier.io.control.heapifyUp := true.B
      stateReg := waitForHeapifyUp
    }
    is(waitForHeapifyUp){
      stateReg := waitForHeapifyUp
      when(heapifier.io.control.done){
        stateReg := idle
        when(io.cmd.op === 0.U && heapifier.io.control.swapped){
          heapifier.io.control.index := io.cmd.refID
          heapifier.io.control.heapifyDown := true.B
          stateReg := waitForHeapifyDown
        }
      }.otherwise{
        heapifier.io.control.heapifyUp := true.B
      }
    }
    is(lastRemoval){
      decHeapsize := true.B
      stateReg := idle
    }
    is(removal0){
      index := heapSizeReg - 1.U
      io.ramReadPort.address := indexToRam
      decHeapsize := true.B
      stateReg := removal1
      when(io.cmd.refID === 0.U){
        stateReg := headRemoval1
      }
    }
    is(removal1){
      index := io.cmd.refID
      io.ramReadPort.address := indexToRam
      tempReg := io.ramReadPort.data
      stateReg := removal2
    }
    is(removal2){
      index := io.cmd.refID
      io.ramWritePort.address := indexToRam
      tempReg := io.ramReadPort.data
      tempReg(indexOffset) := tempReg(indexOffset)
      stateReg := removal3
    }
    is(removal3){
      index := io.cmd.refID
      io.ramWritePort.data := tempReg
      io.ramWritePort.write := true.B
      heapifier.io.control.heapifyUp := true.B
      heapifier.io.control.index := io.cmd.refID
      stateReg := waitForHeapifyUp
    }
    is(headRemoval1){
      index := heapSizeReg
      headReg := io.ramReadPort.data(indexOffset)
      heapifier.io.control.heapifyDown := true.B
      heapifier.io.control.index := io.cmd.refID
      stateReg := waitForHeapifyDown
    }
    is(waitForHeapifyDown){
      stateReg := waitForHeapifyDown
      heapifier.io.control.heapifyDown := true.B
      when(heapifier.io.control.done) {
        stateReg := idle
      }
    }
  }
}
