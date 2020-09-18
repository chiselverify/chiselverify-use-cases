import chisel3._
import chisel3.util._

object Definitions {

  def RamAddressParent(index: UInt, childrenCount: Int, size: Int): (UInt, UInt) = {
    val ramBlock = Wire(UInt(log2Ceil((size/childrenCount).ceil.toInt).W))
    val offset = Wire(UInt(log2Ceil(childrenCount).W))
    ramBlock := ((index - (childrenCount - 1).U) >> (2*log2Ceil(childrenCount))).asUInt()
    offset := (ramBlock << log2Ceil(childrenCount)).asUInt()
    return (ramBlock , offset)
  }

  def RamAddressChildren(indexParent: UInt, childrenCount: Int): UInt ={
    return indexParent
  }
}


object HelpFunctions {

  def fact(n: Int): Int = {
    if (n <= 1)
      return n
    else
      return n * fact(n - 1)
  }
}

