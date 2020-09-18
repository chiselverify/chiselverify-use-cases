import chisel3._

/**
 * Determines the minimum value of a given set of numbers and returns a bit vector
 * with the bits at the indices of the occurrences of the smallest number being asserted.
 * @param n the number of values to be compared
 * @param w the width of a single element
 */
class MinimumFinder(n: Int, w: Int) extends Module{
  val io = IO(new Bundle{
    val values = Input(Vec(n, UInt(w.W)))
    val out = Output(UInt(n.W))
  })

  // calculate the minimum number of required comparators
  var numberOfComparators = 0
  for(i <- 0 until n){ for(j <- i + 1 until n){numberOfComparators += 1}}

  // create a vector of Booleans, which will hold the comparators
  val comparators = Wire(Vec(numberOfComparators, Bool()))
  // create a list which maps every comparator to the two compared values
  var comparatorValueMap = Array[Array[Int]]()

  // iterate through all input values and create a comparator with all higher indexed input values
  var compIndex : Int = 0
  for(leftOperand <- 0 until n){
    for(rightOperand <- leftOperand + 1 until n){
      comparators(compIndex) := io.values(leftOperand) > io.values(rightOperand)
      comparatorValueMap = comparatorValueMap :+ Array(leftOperand, rightOperand)
      compIndex += 1
    }
  }

  // connect comparators to AND-level to get an encoding of which indices point to the smallest number(s)
  // the number(s) are marked by a one in the output at the position equivalent to the index
  val smallest = Wire(Vec(n, Bool()))

  for(input <- 0 until n){
    var found : Int = 0 // number of comparators containing the specific input found
    val wire = Wire(Vec(n - 1, Bool()))
    for(compIndex <- 0 until numberOfComparators){
      if(comparatorValueMap(compIndex)(0) == input){ // comparator outputs 0 when "input" is smaller -> invert
        if(found == 0) wire(found) := !comparators(compIndex) || (io.values(comparatorValueMap(compIndex)(0))===io.values(comparatorValueMap(compIndex)(1)))
        else wire(found) := (!comparators(compIndex) || (io.values(comparatorValueMap(compIndex)(0))===io.values(comparatorValueMap(compIndex)(1)))) && wire(found - 1) // chain AND operations
        found += 1
      }else if(comparatorValueMap(compIndex)(1) == input){ // comparator outputs 1 when "input" is smaller
        if(found == 0) wire(found) := comparators(compIndex) || (io.values(comparatorValueMap(compIndex)(0))===io.values(comparatorValueMap(compIndex)(1)))
        else wire(found) := (comparators(compIndex) || (io.values(comparatorValueMap(compIndex)(0))===io.values(comparatorValueMap(compIndex)(1)))) && wire(found - 1) // chain AND operations
        found += 1
      }
    }
    assert(found == n - 1) // check whether the correct amount of comparators has been identified
    smallest(input) := wire(found - 1)
  }
  io.out := smallest.asUInt()
}