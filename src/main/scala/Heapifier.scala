import chisel3._
import chisel3.util._
import lib._

/**
 * Component implementing the heapify algorithm. Works itself either upwards or downwards
 * through the heap from a given start index and swaps elements to
 * satisfy the min-heap condition.
 *
 * During operation either of the control signals heapifyUp or heapifyDown need to be held high
 * until done is asserted.
 *
 * When done is asserted, the component also signalizes whether a swap has taken place for that clock cycle
 * @param size the size of the heap
 * @param childrenCount the number of children per node (must be power of 2)
 * @param normalPriorityWidth the width of the normal priority
 * @param cyclicPriorityWidth the width of the cyclic priority
 */
class Heapifier(
                size : Int, // max number of elements in queue
                childrenCount : Int, // Number of children per node. Must be 2^m
                normalPriorityWidth : Int, // Width of normal priority
                cyclicPriorityWidth : Int // Width of cyclic priority
                )extends Module{
  val io = IO(new Bundle{
    val control = new Bundle {
      val heapifyUp = Input(Bool())
      val heapifyDown = Input(Bool())
      val done = Output(Bool())
      val swapped = Output(Bool())
      val index = Input(UInt(log2Ceil(size).W))
    }
    val ramReadPort = new Bundle{
      val address = Output(UInt(log2Ceil(size/childrenCount).W))
      val data = Input(Vec(childrenCount,new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
    }
    val ramWritePort = new Bundle{
      val address = Output(UInt(log2Ceil(size/childrenCount).W))
      val data = Output(Vec(childrenCount,new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
      val write = Output(Bool())
    }
    // port to the cached head element stored in a register
    val headPort = new Bundle{
      val rdData = Input(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))
      val wrData = Output(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))
      val write = Output(Bool())
    }

    // TODO: remove debug outputs
    val out = Output(UInt((log2Ceil(childrenCount)+1).W))
    val swap = Output(Bool())
    val state = Output(UInt())
    val minInputs = Output(Vec(childrenCount+1,new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth)))
    val parentOff = Output(UInt(log2Ceil(size).W))
    val nextIndexOut = Output(UInt(log2Ceil(size).W))
    val indexOut = Output(UInt(log2Ceil(size).W))
  })

  // state elements
  val idle :: warmUp1 :: warmDown1 :: warmUp2 :: warmDown2 :: readUp :: readDown :: wbUp1 :: wbDown1 :: wbUp2 :: wbDown2 :: Nil = Enum(11)
  val stateReg = RegInit(idle) // state register
  val indexReg = RegInit(0.U(log2Ceil(size).W)) // register holding the index of the current parent
  val swappedReg = RegInit(false.B) // register holding a flag showing whether a swap has occurred
  val parentReg = RegInit(VecInit(Seq.fill(childrenCount)(0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))))) // register holding the content of the RAM cell containing the parent
  val childrenReg = RegInit(VecInit(Seq.fill(childrenCount)(0.U.asTypeOf(new PriorityBundle(normalPriorityWidth,cyclicPriorityWidth))))) // register holding the content of the RAM cell of the children


  val minFinder = Module(new MinFinder(childrenCount + 1, normalPriorityWidth, cyclicPriorityWidth)) // module to find the minimum priority among the parent and children

  // ram address generation
  val addressIndex = Wire(UInt(log2Ceil(size).W)) // wire that address generation is based on. Is set to indexReg except of the last write back stage, where the next address needs to be generated
  addressIndex := indexReg
  val indexParent = addressIndex
  val ramAddressChildren = addressIndex // the RAM addres of the children equals the index of the parent
  val ramAddressParent = ((addressIndex - 1.U) >> log2Ceil(childrenCount)).asUInt() // the RAM address of the parent is calculated by (index-1)/childrenCount

  // parent selection
  val parentOffset = Mux(indexReg === 0.U, 0.U, indexReg(log2Ceil(childrenCount),0) - 1.U(log2Ceil(size).W)) // the offset of the parent within its RAM cell
  val parent = parentReg(parentOffset) // the actual parent selected from the parent register

  // hook up the minFinder
  minFinder.io.values(0) := parent
  io.minInputs(0) := parent
  for(i <- 0 until childrenCount){
    minFinder.io.values(i + 1) := childrenReg(i)
    io.minInputs(i+1) := childrenReg(i) //TODO: remove debug outputs
  }

  val nextIndexUp = ((indexReg - 1.U) >> log2Ceil(childrenCount)).asUInt() // index of next parent is given by (index-1)/childrenCount
  val nextIndexDown = (indexReg << log2Ceil(childrenCount)).asUInt() + RegNext(minFinder.io.idx) // index of next parent is given by (index * childrenCount) + selected child
  val swapRequired = minFinder.io.idx =/= 0.U // a swap is only required when the parent does not have the highest priority


  // default assignments
  io.control.done := false.B
  io.control.swapped := swappedReg
  io.ramReadPort.address := 0.U
  io.ramWritePort.address := 0.U
  io.ramWritePort.data := parentReg
  io.ramWritePort.write := false.B
  io.headPort.write := false.B
  io.headPort.wrData := parentReg(0)

  // TODO: remove debug outputs
  io.out := minFinder.io.idx
  io.swap := swapRequired
  io.state := stateReg
  io.parentOff := parentOffset
  io.nextIndexOut := Mux(io.control.heapifyDown,nextIndexDown,nextIndexUp)
  io.indexOut := indexReg

  // the state machine is separated into 2 switch statements:
  // - one dealing with the state flow and control of state flow relevant elements
  // - the other one dealing with data flow and bus operation

  // state machine flow
  switch(stateReg){
    is(idle){ // in idle we wait for a control signal, update out index register, and hold the swapped flag low
      io.control.done := true.B
      indexReg := io.control.index
      swappedReg := false.B
      when(io.control.heapifyUp){
        stateReg := warmUp1
      }.elsewhen(io.control.heapifyDown){
        stateReg := warmDown1
      }.otherwise{
        stateReg := idle
      }
    }
    is(warmUp1){
      stateReg := warmUp2
    }
    is(warmUp2){
      stateReg := readUp
    }
    is(readUp){
      stateReg := wbUp1
    }
    is(wbUp1){
      stateReg := wbUp2
      when(!swapRequired){ // when no swap is required we go into idle state
        io.control.done := true.B
        stateReg := idle
      }.otherwise{ // we have swapped
        swappedReg := true.B
      }
    }
    is(wbUp2){ // update the index register and apply new index to address generation
      stateReg := readUp
      indexReg := nextIndexUp
      addressIndex := nextIndexUp
      when(indexReg === 0.U){ // we have reached the root and can go to idle
        io.control.done := true.B
        stateReg := idle
      }
    }
    is(warmDown1){
      stateReg := warmDown2
    }
    is(warmDown2){
      stateReg := readDown
    }
    is(readDown){
      stateReg := wbDown1
    }
    is(wbDown1){
      stateReg := wbDown2
      when(!swapRequired){ // when no swap is required we go into idle state
        io.control.done := true.B
        stateReg := idle
      }.otherwise{ // we have swapped
        swappedReg := true.B
      }
    }
    is(wbDown2){ // update the index register and apply new index to address generation
      stateReg := readDown
      indexReg := nextIndexDown
      addressIndex := nextIndexDown
      when((nextIndexDown<<log2Ceil(childrenCount)).asUInt >= size.U){ // we have reached a childless index and can go to idle
        io.control.done := true.B
        stateReg := idle
      }
    }
  }

  // data and bus control
  switch(stateReg){
    /////////////////////////////// up control
    is(warmUp1){ // apply childrens RAM address to read port
      io.ramReadPort.address := ramAddressChildren
    }
    is(warmUp2){ // apply parents RAM address to read port and save children
      io.ramReadPort.address := ramAddressParent
      childrenReg := io.ramReadPort.data
    }
    is(readUp){ // apply childrens RAM address to write port
      io.ramWritePort.address := ramAddressChildren
      when(indexReg === 0.U){ // if parent is head -> use head port
        parentReg := parentReg
        parentReg(0.U) := io.headPort.rdData
      }.otherwise{ // if not read from RAM
        parentReg := io.ramReadPort.data
      }
    }
    is(wbUp1){ // write back the updated children RAM cell if a swap is required and update the parent register
      io.ramWritePort.address := ramAddressParent
      when(swapRequired){
        parentReg := parentReg
        parentReg(parentOffset) := minFinder.io.res
        io.ramWritePort.data := childrenReg
        io.ramWritePort.data(minFinder.io.idx - 1.U) := parent
        io.ramWritePort.write := true.B
      }
    }
    is(wbUp2){ // write back the parent register and transfer the parent RAM cell to the children register
      io.ramReadPort.address := ramAddressParent
      childrenReg := parentReg
      when(swapRequired){
        when(indexReg === 0.U){ // write via head port if parent is head
          io.headPort.wrData := minFinder.io.res
          io.headPort.write := true.B
        }.otherwise{ // else use the RAM port
          io.ramWritePort.data := parentReg
          io.ramWritePort.write := true.B
        }
      }
    }
    ////////////////////////////// down control
    is(warmDown1){ // apply parents RAM address to read port
      io.ramReadPort.address := ramAddressParent
    }
    is(warmDown2){ // apply childrens RAM address to read port and save parent
      io.ramReadPort.address := ramAddressChildren
      when(indexReg === 0.U){ // if parent is head -> use head port
        parentReg := parentReg
        parentReg(0.U) := io.headPort.rdData
      }.otherwise{ // if not read from RAM
        parentReg := io.ramReadPort.data
      }
    }
    is(readDown){ // apply parents RAM address to write port and save children
      io.ramWritePort.address := ramAddressParent
      childrenReg := io.ramReadPort.data
    }
    is(wbDown1){ // write back the updated parent RAM cell if a swap is required and update the children register
      io.ramWritePort.address := ramAddressChildren
      when(swapRequired){
        childrenReg := childrenReg
        childrenReg(minFinder.io.idx - 1.U) := parent
        when(indexReg === 0.U){
          io.headPort.wrData := minFinder.io.res
          io.headPort.write := true.B
        }.otherwise{
          io.ramWritePort.data := parentReg
          io.ramWritePort.data(parentOffset) := minFinder.io.res
          io.ramWritePort.write := true.B
        }
      }
    }
    is(wbDown2){ // write back the children register and transfer the children RAM cell to the parent register
      io.ramReadPort.address := ramAddressChildren
      parentReg := childrenReg
      when(swapRequired){
        io.ramWritePort.data := childrenReg
        io.ramWritePort.write := true.B
      }
    }
  }

}
