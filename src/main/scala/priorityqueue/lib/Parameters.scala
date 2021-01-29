package priorityqueue.lib

import chisel3.util.log2Ceil

object Parameters {
  var cWid = 0
  var nWid = 0
  var rWid = 0
  var addrWid = 0
  var offsetWid = 0
  val opWid = 3

  var size = 0
  var superCycleCnt = 0
  var cycleCnt = 0
  var childrenCnt = 0
  var memSize = 0

  def setParameters(size: Int, superCycleCnt: Int, cycleCnt: Int, childrenCnt: Int): Unit = {
    cWid = log2Ceil(superCycleCnt)
    nWid = log2Ceil(cycleCnt)
    rWid = log2Ceil(size * childrenCnt)
    addrWid = log2Ceil(size)
    offsetWid = log2Ceil(childrenCnt)
    memSize = childrenCnt * size

    this.size = size
    this.superCycleCnt = superCycleCnt
    this.cycleCnt = cycleCnt
    this.childrenCnt = childrenCnt
  }
}