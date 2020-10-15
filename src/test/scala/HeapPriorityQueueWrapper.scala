import chisel3.iotesters.PeekPokeTester

class HeapPriorityQueueWrapper(dut: HeapPriorityQueue, size: Int, chCount: Int, debugLvl: Int)(cWid : Int,nWid : Int,rWid : Int) extends PeekPokeTester(dut){
  var pipedRdAddr = 0
  var pipedWrAddr = 0
  var searchSimDelay = 0
  val states = Array("idle" ,"headInsertion", "normalInsertion", "initSearch", "waitForSearch", "resetCell", "lastRemoval", "headRemoval", "tailRemoval" ,"removal", "waitForHeapifyUp", "waitForHeapifyDown")
  val heapifierStates = Array("idle", "warmUp1", "warmDown1", "warmUp2", "warmDown2", "readUp", "readDown", "wbUp1", "wbDown1", "wbUp2", "wbDown2")
  val mem = Array.fill(size-1)(Array(Math.pow(2,cWid).toInt-1,Math.pow(2,nWid).toInt-1,Math.pow(2,rWid).toInt-1)).sliding(chCount,chCount).toArray
  def stepDut(n: Int) : Unit = {
    for(i <- 0 until n){
      // read port
      try {
        for (i <- 0 until chCount) {
          // ignores reads outside of array
          poke(dut.io.rdPort.data(i).prio.cycl, mem(pipedRdAddr)(i)(0))
          poke(dut.io.rdPort.data(i).prio.norm, mem(pipedRdAddr)(i)(1))
          poke(dut.io.rdPort.data(i).id, mem(pipedRdAddr)(i)(2))
        }
      } catch {
        case e: IndexOutOfBoundsException => {}
      }
      // write port
      if (peek(dut.io.wrPort.write) == 1) {
        for (i <- 0 until chCount) {
          if((peek(dut.io.wrPort.mask) & (BigInt(1) << i)) != 0){
            mem(pipedWrAddr)(i)(0) = peek(dut.io.wrPort.data(i).prio.cycl).toInt
            mem(pipedWrAddr)(i)(1) = peek(dut.io.wrPort.data(i).prio.norm).toInt
            mem(pipedWrAddr)(i)(2) = peek(dut.io.wrPort.data(i).id).toInt
          }
        }
      }
      // search port
      if(peek(dut.io.srch.search)==1){
        if(searchSimDelay > 3){
          var idx = peek(dut.io.head.refID).toInt
          if(idx != peek(dut.io.srch.refID).toInt){
            idx = mem.flatten.map(_(2)==peek(dut.io.srch.refID)).indexOf(true) + 1
          }
          if(idx == -1){
            poke(dut.io.srch.error, 1)
          }else{
            poke(dut.io.srch.res, idx)
          }
          poke(dut.io.srch.done, 1)
          searchSimDelay = 0
        }else{
          poke(dut.io.srch.done, 0)
          poke(dut.io.srch.error, 0)
          searchSimDelay += 1
        }
      }else{
        searchSimDelay = 0
      }

      if (debugLvl >= 2) {
        println(s"States: ${states(peek(dut.io.debug.state).toInt)} || ${heapifierStates(peek(dut.io.debug.heapifierState).toInt)} at index ${peek(dut.io.debug.heapifierIndex)}\n"+
          s"ReadPort: ${peek(dut.io.rdPort.address)} | ${mem.apply(pipedRdAddr).map(_.mkString(":")).mkString(",")}\n"+
          s"WritePort: ${peek(dut.io.wrPort.address)} | ${peek(dut.io.wrPort.data).sliding(3,3).map(_.mkString(":")).mkString(",")} | ${peek(dut.io.wrPort.write)} | ${peek(dut.io.wrPort.mask).toString(2)}\n"+
          s"Memory:\n${peek(dut.io.head.prio).values.mkString(":")}:${peek(dut.io.head.refID)}\n${mem.map(_.map(_.mkString(":")).mkString(", ")).mkString("\n")}")
      }

      // simulate synchronous memory
      pipedRdAddr = peek(dut.io.rdPort.address).toInt
      pipedWrAddr = peek(dut.io.wrPort.address).toInt

      step(1)
    }
  }
  def stepUntilDone(max: Int = Int.MaxValue) : Unit = {
    var iterations = 0
    while(iterations < max && (peek(dut.io.cmd.done)==0 || iterations < 1)){
      stepDut(1)
      iterations += 1
    }
  }
  def pokeID(id: Int) : Unit = {
    poke(dut.io.cmd.refID, id)
  }
  def pokePriority(c: Int, n: Int) : Unit = {
    poke(dut.io.cmd.prio.norm,n)
    poke(dut.io.cmd.prio.cycl,c)
  }
  def pokePrioAndID(c: Int, n: Int, id: Int) : Unit = {
    pokePriority(n,c)
    pokeID(id)
  }
  def insert(c: Int, n: Int, id: Int) : Unit = {
    if(debugLvl >= 1){
      println(s"Inserting $c:$n:$id-------------------------")
    }
    pokePrioAndID(n,c,id)
    poke(dut.io.cmd.op, 1)
    poke(dut.io.cmd.valid,1)
    stepUntilDone()
    poke(dut.io.cmd.valid,0)
  }
  def remove(id: Int) : Unit = {
    if(debugLvl >= 1){
      println(s"Removing $id--------------------------------")
    }
    pokeID(id)
    poke(dut.io.cmd.op, 0)
    poke(dut.io.cmd.valid,1)
    stepUntilDone(40)
    poke(dut.io.cmd.valid, 0)
  }
  def printMem() : Unit = {
    println(s"Memory:\n${peek(dut.io.head.prio).values.mkString(":")}:${peek(dut.io.head.refID)}\n${mem.map(_.map(_.mkString(":")).mkString(", ")).mkString("\n")}")
  }
}