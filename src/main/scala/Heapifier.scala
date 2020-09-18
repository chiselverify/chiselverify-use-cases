import chisel3._
import chisel3.util._
import Definitions._

class Heapifier(
                 size : Int, // max number of elements in queue
                 childrenCount : Int, // Number of children per node. Must be 2^m
                 normalPriorityWidth : Int, // Width of normal priority
                 cyclicPriorityWidth : Int // Width of cyclic priority
               )extends Module{
  val io = IO(new Bundle{
    val control = new Bundle {
      val heapifyUp: Bool = Input(Bool())
      val heapifyDown: Bool = Input(Bool())
      val done: Bool = Output(Bool())
      val swap: Bool = Output(Bool())
    }
    val ramReadPort = new Bundle{
      val address: UInt = Output(UInt(log2Ceil(size/childrenCount).W))
      val data: Vec[UInt] = Input(Vec(childrenCount,UInt((normalPriorityWidth+cyclicPriorityWidth).W)))
    }
    val ramWritePort = new Bundle{
      val address: UInt = Output(UInt(log2Ceil(size/childrenCount).W))
      val data: Vec[UInt] = Output(Vec(childrenCount,UInt((normalPriorityWidth+cyclicPriorityWidth).W)))
      val write: Bool = Output(Bool())
    }
    val index: UInt = Input(UInt(log2Ceil(size).W))

    // debug outputs
    val out = Output(UInt((log2Ceil(childrenCount)+1).W))
    val swap = Output(Bool())
    val state = Output(UInt())
    val minInputs = Output(Vec(childrenCount+1,UInt((normalPriorityWidth+cyclicPriorityWidth).W)))
    val parentOff = Output(UInt(log2Ceil(size).W))
  })

  ////////////////////////////////// Memory elements //////////////////////////////////

  // state machine setup
  val idle :: step1Up :: step2Up :: step3Up :: Nil = Enum(4)
  val stateReg: UInt = RegInit(idle)
  io.state := stateReg

  // index
  val indexReg = RegInit(0.U(log2Ceil(size).W))
  val indexWire = Wire(UInt(log2Ceil(size).W))
  indexWire := indexReg

  // registers to hold RAM data
  val parentReg: Vec[UInt] = RegInit(VecInit(Seq.fill(childrenCount)(0.U((normalPriorityWidth+cyclicPriorityWidth).W))))
  val childrenReg: Vec[UInt] = RegInit(VecInit(Seq.fill(childrenCount)(0.U((normalPriorityWidth+cyclicPriorityWidth).W))))

  // saved values for write back
  val updateSavedRegs = WireDefault(false.B)
  val ramAddressParentWB = RegInit(0.U(log2Ceil(size/childrenCount).W))
  val ramAddressChildrenWB = RegInit(0.U(log2Ceil(size/childrenCount).W))
  val parentOffsetReg = RegInit(0.U(log2Ceil(childrenCount).W))

  /////////////////////////////////////////////////////////////////////////////////////

  // ram addressing
  val ramAddressOfIndex = Wire(UInt(log2Ceil(size/childrenCount).W))
  val ramAddressChildren = Wire(UInt(log2Ceil(size/childrenCount).W))
  val ramAddressParent = Wire(UInt(log2Ceil(size/childrenCount).W))
  val indexParent = Wire(UInt(log2Ceil(size).W))

  ramAddressOfIndex := ((indexWire - 1.U) >> log2Ceil(childrenCount)).asUInt()
  // ram addresses for heapify up
  ramAddressChildren := ramAddressOfIndex
  indexParent := ramAddressOfIndex
  ramAddressParent := ((indexParent - 1.U) >> log2Ceil(childrenCount)).asUInt()
  // ram addresses for heapify down
  when(io.control.heapifyDown){
    ramAddressParent := ramAddressOfIndex
    ramAddressChildren := indexWire
    indexParent := indexWire
  }

  // offset of parent in ram block
  val parentOffset = indexParent - (ramAddressParent << log2Ceil(childrenCount)).asUInt()

  // save registers for write back
  when(updateSavedRegs){
    ramAddressParentWB := ramAddressParent
    ramAddressChildrenWB := ramAddressChildren
    parentOffsetReg := parentOffset
  }

  // select parent from ram block and connect parent and children to minimum finder
  val parent: UInt = parentReg(parentOffsetReg)

  val prioMinimumFinder = Module(new PriorityMinimumFinder(childrenCount + 1, normalPriorityWidth, cyclicPriorityWidth))
  prioMinimumFinder.io.values(0) := parent
  io.minInputs(0) := parent
  for(i <- 0 until childrenCount){
    prioMinimumFinder.io.values(i + 1) := io.ramReadPort.data(i)
    io.minInputs(i+1) := io.ramReadPort.data(i)
  }

  // swap is required when parent is not the smallest
  val swapRequired: Bool = prioMinimumFinder.io.out =/= 0.U
  // 2 more memory elements
  val swapWasRequired: Bool = RegNext(swapRequired)
  val childToSwap: UInt = RegNext(prioMinimumFinder.io.out) - 1.U

  io.control.done := false.B
  io.ramReadPort.address := ramAddressParent
  io.ramWritePort.address := ramAddressParent
  io.ramWritePort.data := parentReg
  io.ramWritePort.write := false.B
  io.control.swap := false.B
  io.parentOff := parentOffset
  io.swap := swapRequired
  io.out := prioMinimumFinder.io.out

  // state machine
  switch(stateReg){
    is(idle){
      stateReg := idle
      io.control.done := true.B
      indexReg := io.index
      indexWire := io.index
      when(io.control.heapifyUp || io.control.heapifyDown){
        stateReg := step1Up
      }
    }
    is(step1Up){
      stateReg := step2Up
      indexReg := indexParent
    }
    is(step2Up){
      stateReg := idle
      io.control.done := true.B
      when(swapRequired){
        io.control.done := false.B
        stateReg := step3Up
      }
    }
    is(step3Up){
      stateReg := step2Up
      when(ramAddressParent =/= 0.U){
        indexReg := indexParent
      }.otherwise{
        stateReg := idle
      }
    }
  }
  // control of fetch
  switch(stateReg){
    is(idle){
      io.ramReadPort.address := ramAddressParent
    }
    is(step1Up){
      parentReg := io.ramReadPort.data
      io.ramReadPort.address := ramAddressChildren
    }
    is(step2Up){
      childrenReg := io.ramReadPort.data
      io.ramReadPort.address := ramAddressParent
    }
    is(step3Up){
      parentReg := io.ramReadPort.data
      io.ramReadPort.address := ramAddressChildren
    }
  }
  // control of write back
  switch(stateReg){
    is(idle){
      // do nothing
    }
    is(step1Up){
      io.ramWritePort.address := ramAddressParent
      updateSavedRegs := true.B
    }
    is(step2Up){
      io.ramWritePort.address := ramAddressChildrenWB
      when(swapRequired){
        io.ramWritePort.data := parentReg
        io.ramWritePort.data(parentOffsetReg) := io.ramReadPort.data(prioMinimumFinder.io.out-1.U)
        io.ramWritePort.write := true.B
      }
    }
    is(step3Up){
      io.ramWritePort.address := ramAddressParent
      updateSavedRegs := true.B
      when(swapWasRequired){
        io.ramWritePort.data := childrenReg
        io.ramWritePort.data(childToSwap) := parent
        io.ramWritePort.write := true.B
      }
    }
  }
}
