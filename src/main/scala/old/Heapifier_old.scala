package old

import chisel3._
import chisel3.util._

class Heapifier_old(
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
    val headPort = new Bundle{
      val rdData: UInt = Input(UInt((normalPriorityWidth+cyclicPriorityWidth).W))
      val wrData: UInt = Output(UInt((normalPriorityWidth+cyclicPriorityWidth).W))
      val write: Bool = Output(Bool())
    }
    val index: UInt = Input(UInt(log2Ceil(size).W))

    // debug outputs
    val out = Output(UInt((log2Ceil(childrenCount)+1).W))
    val swap = Output(Bool())
    val state = Output(UInt())
    val minInputs = Output(Vec(childrenCount+1,UInt((normalPriorityWidth+cyclicPriorityWidth).W)))
    val parentOff = Output(UInt(log2Ceil(size).W))
    val nextIndexOut = Output(UInt(log2Ceil(size).W))
    val indexOut = Output(UInt(log2Ceil(size).W))
  })
  ////////////////////////////////////// modules ///////////////////////////////////////
  val prioMinimumFinder = Module(new PriorityMinimumFinder(childrenCount + 1, normalPriorityWidth, cyclicPriorityWidth))
  //////////////////////////////////////////////////////////////////////////////////////

  ////////////////////////////////// Memory elements //////////////////////////////////
  // state machine setup
  val idle :: rdParent :: rdParent_wrChildren  :: rdChildren :: wrParent :: Nil = Enum(5)
  val stateReg: UInt = RegInit(idle)
  io.state := stateReg

  // index
  val indexReg = RegInit(0.U(log2Ceil(size).W))
  val indexWire = Wire(UInt(log2Ceil(size).W))
  indexWire := indexReg

  // registers to hold RAM data
  val parentReg: Vec[UInt] = Reg(Vec(childrenCount,UInt((normalPriorityWidth+cyclicPriorityWidth).W)))
  val childrenReg: Vec[UInt] = Reg(Vec(childrenCount,UInt((normalPriorityWidth+cyclicPriorityWidth).W)))

  // saved values for write back
  val updateSavedRegs = WireDefault(false.B)
  val ramAddressChildrenWB = RegInit(0.U(log2Ceil(size/childrenCount).W))
  val parentOffsetReg = RegInit(0.U(log2Ceil(childrenCount).W))
  val childToSwap = RegInit(0.U(log2Ceil(childrenCount+1).W))
  val swapWasRequired = RegInit(false.B)
  val swapRequired = WireDefault(false.B)
  /////////////////////////////////////////////////////////////////////////////////////

  // ram addressing
  val ramAddressChildren = Wire(UInt(log2Ceil(size/childrenCount).W))
  val ramAddressParent = Wire(UInt(log2Ceil(size/childrenCount).W))
  val indexParent = Wire(UInt(log2Ceil(size).W))

  ramAddressParent := ((indexWire - 1.U) >> log2Ceil(childrenCount)).asUInt()
  ramAddressChildren := indexWire
  indexParent := indexWire

  // offset of parent in ram block
  val parentOffset = indexParent - (ramAddressParent << log2Ceil(childrenCount)).asUInt()

  // next index depending on heapify up/down
  val nextIndex = Wire(UInt(log2Ceil(size).W))
  nextIndex := indexReg
  when(io.control.heapifyUp){
    when(indexReg =/= 0.U){
      nextIndex := ramAddressParent
    }
  }.elsewhen(io.control.heapifyDown){
    when(indexReg <= ((size/2)+1).U){
      nextIndex := (ramAddressChildren << log2Ceil(childrenCount)).asUInt() + prioMinimumFinder.io.out
    }
  }
  //////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////// read side /////////////////////////////////////////////////


  //////////////////////////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////// write side /////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////////////////////////

  // update saved registers for write back
  when(stateReg === rdChildren){
    ramAddressChildrenWB := ramAddressChildren
    parentOffsetReg := parentOffset
    childToSwap := prioMinimumFinder.io.out - 1.U
    swapWasRequired := swapRequired
  }

  // select parent from ram block and connect parent and children to minimum finder
  val isRoot = RegInit(false.B)
  val parent: UInt = Wire(UInt((normalPriorityWidth+cyclicPriorityWidth).W))

  when(stateReg === rdParent_wrChildren || stateReg === rdParent){
    isRoot := indexReg === 0.U
  }
  parent := parentReg(parentOffsetReg)
  when(isRoot){
    parent := io.headPort.rdData
  }

  prioMinimumFinder.io.values(0) := parent
  io.minInputs(0) := parent
  for(i <- 0 until childrenCount){
    prioMinimumFinder.io.values(i + 1) := io.ramReadPort.data(i)
    io.minInputs(i+1) := io.ramReadPort.data(i)
  }
  swapRequired := prioMinimumFinder.io.out =/= 0.U

  val stopNextTime = RegInit(false.B)

  io.control.done := false.B
  io.ramReadPort.address := 0.U
  io.ramWritePort.address := 0.U
  io.ramWritePort.data := Seq.fill(childrenCount)(0.U)
  io.ramWritePort.write := false.B
  io.control.swap := false.B
  io.parentOff := parentOffset
  io.swap := swapRequired
  io.out := prioMinimumFinder.io.out
  io.headPort.write := false.B
  io.headPort.wrData := 0.U
  io.nextIndexOut := nextIndex
  io.indexOut := indexReg

  // state machine
  switch(stateReg){
    is(idle){
      stateReg := idle
      io.control.done := true.B
      indexReg := io.index
      indexWire := io.index
      when(io.control.heapifyUp || io.control.heapifyDown){
        stateReg := rdParent
      }
    }
    is(rdParent){
      stateReg := rdChildren
      parentOffsetReg := parentOffset
    }
    is(rdParent_wrChildren){
      stateReg := rdChildren
      parentOffsetReg := parentOffset
    }
    is(rdChildren){
      stateReg := wrParent
      stopNextTime := indexReg === nextIndex
      when(!swapRequired || stopNextTime){
        stateReg := idle
        io.control.done := true.B
      }.otherwise{
        indexReg := nextIndex
      }
    }
    is(wrParent){
      stateReg := rdParent_wrChildren
    }
  }
  // read control
  switch(stateReg){
    is(idle){
      io.ramReadPort.address := ramAddressParent
    }
    is(rdParent){
      parentReg := io.ramReadPort.data
      io.ramReadPort.address := ramAddressChildren
    }
    is(rdParent_wrChildren){
      parentReg := io.ramReadPort.data
      io.ramReadPort.address := ramAddressChildren
    }
    is(rdChildren){
      childrenReg := io.ramReadPort.data
    }
    is(wrParent){
      io.ramReadPort.address := ramAddressParent
    }
  }
  // write control
  switch(stateReg){
    is(idle){
      // do nothing
    }
    is(rdParent){
      // do nothing
    }
    is(rdParent_wrChildren){
      when(swapWasRequired){
        io.ramWritePort.data := childrenReg
        io.ramWritePort.data(childToSwap) := parent
        io.ramWritePort.write := true.B
      }
    }
    is(rdChildren){
      when(!isRoot){
        io.ramWritePort.address := ramAddressParent
      }
    }
    is(wrParent){
      io.ramWritePort.address := ramAddressChildrenWB
      when(swapWasRequired){
        when(!isRoot){
          io.ramWritePort.data := parentReg
          io.ramWritePort.data(parentOffsetReg) := childrenReg(childToSwap)
          io.ramWritePort.write := true.B
        }.otherwise{
          io.headPort.wrData := childrenReg(childToSwap)
          io.headPort.write := true.B
        }
      }
    }
  }
}
