import chisel3._
import chisel3.util._
import lib._

//TODO: do we need to reset cells to max values => would errors due to elements outside of heap tree occur?
// -> set flag when on last used address and deactivate inputs accordingly to minfinder
//TODO: catch unsuccessfull operations

class HeapPrioQ(
                 size : Int, // max number of elements in queue
                 chCount : Int, // Number of children per node. Must be 2^m
                 nWid : Int, // Width of normal priority
                 cWid : Int, // Width of cyclic priority
                 rWid : Int // Width of reference. Must be >= clog2(SIZE)
                  ) extends Module{
  val io = IO(new Bundle{
    // =====================================================
    // Interface for signaling head element to user.
    // I.e. the element with the lowest priority
    // -----------------------------------------------------
    val head = new Bundle{
      val valid = Output(Bool()) //verify
      val none = Output(Bool()) //TODO
      val prio = Output(new PriorityAndID(nWid,cWid,rWid))
      val refID = Output(UInt(rWid.W))
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
      val prio = Input(new Priority(nWid,cWid))
      val refID = Input(UInt(rWid.W))
      // outputs
      val done = Output(Bool()) //VERIFY
      val result = Output(Bool()) // 0=Success, 1=Failure //TODO
      val rm_prio = Output(new Priority(nWid,cWid)) //TODO
    }

    val rdPort = new rdPort(log2Ceil(size/chCount),Vec(chCount,new PriorityAndID(nWid,cWid,rWid)))
    val wrPort = new wrPort(log2Ceil(size/chCount),log2Ceil(chCount),Vec(chCount,new PriorityAndID(nWid,cWid,rWid)))
    val srch = new searchPort(size,rWid)

    val debug = new Bundle{
      val state = Output(UInt())
      val heapifierState = Output(UInt())
      val heapifierIndex = Output(UInt(log2Ceil(size).W))
      val heapifierWrite = Output(Bool())
      val minOut = Output(UInt(log2Ceil(chCount).W))
      val minInputs = Output(Vec(chCount+1,new Priority(nWid,cWid)))
      val swap = Output(Bool())
    }
  })
  if(!isPow2(chCount)) throw new Exception("The number of children must be a power of 2!")

  // modules
  val heapifier = Module(new Heapifier(size, chCount, nWid, cWid, rWid))

  // state elements
  val idle :: headInsertion :: normalInsertion :: waitForSearch :: lastRemoval :: headRemoval :: removal0 :: removal1 :: waitForHeapifyUp :: waitForHeapifyDown :: Nil = Enum(13)
  val stateReg = RegInit(idle)
  val heapSizeReg = RegInit(0.U(log2Ceil(size+1).W))
  val headReg = RegInit(0.U.asTypeOf(new PriorityAndID(nWid,cWid,rWid)))
  val tempReg = RegInit(VecInit(Seq.fill(chCount)(0.U.asTypeOf(new PriorityAndID(nWid,cWid,rWid)))))
  val headValid = RegInit(true.B)
  val removalIndex = RegInit(0.U(log2Ceil(size).W))

  // ram write address and offset
  val wrIndex = WireDefault(0.U(log2Ceil(size).W))
  val wrIndexToRam = ((wrIndex - 1.U) >> log2Ceil(chCount)).asUInt
  val wrIndexOffset = Mux(wrIndex === 0.U, 0.U, wrIndex(log2Ceil(chCount),0) - 1.U(log2Ceil(size).W))

  // ram read address and offset
  val rdIndex = WireDefault(0.U(log2Ceil(size).W))
  val rdIndexToRam = ((rdIndex - 1.U) >> log2Ceil(chCount)).asUInt
  val rdIndexOffset = Mux(rdIndex === 0.U, 0.U, rdIndex(log2Ceil(chCount),0) - 1.U(log2Ceil(size).W))

  // heapSize controlling
  val incHeapsize = heapSizeReg + 1.U
  val decHeapsize = heapSizeReg - 1.U

  // connect heapifier
  heapifier.io.control.heapifyUp := false.B
  heapifier.io.control.heapifyDown := false.B
  heapifier.io.control.idx := heapSizeReg
  heapifier.io.control.heapSize := heapSizeReg
  heapifier.io.headPort.rdData := headReg
  when(heapifier.io.headPort.write){
    headReg := heapifier.io.headPort.wrData
  }
  // default ram connections
  io.rdPort.address := heapifier.io.rdPort.address
  heapifier.io.rdPort.data := io.rdPort.data
  io.wrPort.address := heapifier.io.wrPort.address
  io.wrPort.data := heapifier.io.wrPort.data
  io.wrPort.write := heapifier.io.wrPort.write
  io.wrPort.mask := 0.U

  // default assignments
  io.head.prio := headReg.prio
  io.head.none := heapSizeReg === 0.U //TODO
  io.head.valid := headValid //verify
  io.head.refID := headReg.id
  io.cmd.done := false.B //TODO
  io.cmd.result := false.B //TODO
  io.cmd.rm_prio := 0.U.asTypeOf(new Priority(nWid,cWid)) //TODO
  io.srch.refID := io.cmd.refID
  io.srch.search := false.B
  io.srch.heapSize := heapSizeReg

  //TODO: remove debug outputs
  io.debug.state := stateReg
  io.debug.heapifierState := heapifier.io.state
  io.debug.heapifierIndex := heapifier.io.indexOut
  io.debug.heapifierWrite := heapifier.io.wrPort.write
  io.debug.minOut := heapifier.io.out
  io.debug.minInputs := heapifier.io.minInputs
  io.debug.swap := heapifier.io.swap


  //TODO: check whether done is set correctly
  //TODO: check whether head valid is set correctly
  //TODO: set "none"
  //TODO: do we need to splice/reset unused mem cells to all high?
  switch(stateReg){
    is(idle){
      io.cmd.done := true.B

      wrIndex := heapSizeReg // prepare write port for insertion
      io.wrPort.address := wrIndexToRam

      when(heapSizeReg =/= 0.U){ // reset valid flag if queue not empty
        headValid := true.B
      }

      when(io.cmd.valid){
         when(io.cmd.op){ // insertion
           headValid := false.B
           stateReg := normalInsertion
           when(heapSizeReg === 0.U){
             stateReg := headInsertion
           }
         }.otherwise{ // removal
           when(heapSizeReg === 1.U){ // beaware: refID is disregarded when last element is removed
             headValid := false.B
             stateReg := lastRemoval
           }.otherwise{
             stateReg := waitForSearch
             io.srch.search := true.B
           }
         }
      }
    }
    is(headInsertion){ // insertion into empty queue
      headReg.prio := io.cmd.prio
      headReg.id := io.cmd.refID
      heapSizeReg := incHeapsize

      stateReg := idle
    }
    is(normalInsertion){ // insertion into already filled queue
      // write new priority
      wrIndex := heapSizeReg
      io.wrPort.data(wrIndexOffset).prio := io.cmd.prio
      io.wrPort.data(wrIndexOffset).id := io.cmd.refID
      io.wrPort.mask := wrIndexOffset
      io.wrPort.write := true.B

      // increase heap size
      heapSizeReg := incHeapsize

      // initiate heapify up
      heapifier.io.control.idx := Mux(heapSizeReg < (chCount+2).U, 0.U, ((heapSizeReg - 1.U) << log2Ceil(chCount)).asUInt)
      heapifier.io.control.heapifyUp := true.B
      stateReg := waitForHeapifyUp
    }
    is(waitForSearch){ // wait for memory to look up the index corresponding to the reference ID
      // prepare ram read port
      rdIndex := decHeapsize
      io.rdPort.address := rdIndexToRam

      stateReg := waitForSearch
      when(io.srch.error){
        stateReg := idle // TODO: add error support
      }.elsewhen(io.srch.done){
        removalIndex := io.srch.res // save index of removal
        when(io.srch.res === 0.U){ // head is being removed
          headValid := false.B
          stateReg := headRemoval
        }.otherwise{ // other element is being removed
          stateReg := removal0
        }
      }
    }
    is(lastRemoval){ // last element is removed from queue
      heapSizeReg := decHeapsize
      stateReg := idle
    }
    is(headRemoval){ // remove head from queue
      heapSizeReg := decHeapsize

      // overwrite cached head element
      rdIndex := decHeapsize
      headReg := io.rdPort.data(wrIndexOffset)

      // initiate heapify down
      heapifier.io.control.heapifyDown := true.B
      heapifier.io.control.idx := io.cmd.refID
      stateReg := waitForHeapifyDown
    }
    is(removal0){ // normal removal
      heapSizeReg := decHeapsize

      // read last queue element from ram into temporary register
      tempReg := io.rdPort.data

      // prepare ram write port
      wrIndex := removalIndex
      io.wrPort.address := wrIndexToRam

      stateReg := removal1
    }
    is(removal1){ // normal removal step 2
      // overwrite the element to be deleted with tail
      wrIndex := removalIndex
      rdIndex := heapSizeReg
      io.wrPort.data(wrIndexOffset) := tempReg(rdIndexOffset)
      io.wrPort.mask := wrIndexOffset
      io.wrPort.write := true.B

      // initiate heapify up
      heapifier.io.control.heapifyUp := true.B
      heapifier.io.control.idx := removalIndex
      stateReg := waitForHeapifyUp
    }
    is(waitForHeapifyUp){ // wait for the heapifier to complete one up pass
      stateReg := waitForHeapifyUp
      when(heapifier.io.control.done){
        stateReg := idle
        when(io.cmd.op === 0.U && !heapifier.io.control.swapped){ // when no swap occurred during removal -> heapify down
          heapifier.io.control.idx := io.cmd.refID
          heapifier.io.control.heapifyDown := true.B
          stateReg := waitForHeapifyDown
        }
      }.otherwise{
        heapifier.io.control.heapifyUp := true.B
      }
    }
    is(waitForHeapifyDown){ // wait for the heapifier to complete one down pass
      stateReg := waitForHeapifyDown
      when(heapifier.io.control.done) {
        stateReg := idle
      }.otherwise{
        heapifier.io.control.heapifyDown := true.B
      }
    }
  }
}
