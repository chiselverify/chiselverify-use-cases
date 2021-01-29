package priorityqueue.models

import priorityqueue.lib.PriorityType

object MinFinderModel {

  def main() : Unit = {
    val values = Array.tabulate(4)(i => PriorityType(i,2,3))
    val res = minFinder(values, 2)
    println(res)
  }

  def minFinder(values: Array[PriorityType], currentSC: Int): (Int, PriorityType) = {
    var smallest = values.head
    for(child <- values){
      if(!smallest.isActive || (child.isSmallerThan(smallest,currentSC) && child.isActive)) smallest = child
    }
    (values.indexOf(smallest),smallest)
  }

}
