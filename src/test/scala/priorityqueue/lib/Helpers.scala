package priorityqueue.lib

import chisel3._
import priorityqueue.lib.BundleTypes._
import priorityqueue.lib.Parameters._
import chiseltest._

/**
 * contains useful conversion as well as poke and peek methods for user defined bundles
 */
object Helpers {

  def randPow2Max(pow: Int): Int = {
    scala.util.Random.nextInt(Math.pow(2, pow).toInt)
  }

  def pokePrioID(port: PriorityID, poke: Seq[Int] = null): Seq[Int] = {
    if (poke != null) {
      port.prio.c.poke(poke.head.U)
      port.prio.n.poke(poke(1).U)
      port.id.poke(poke(2).U)
      port.active.poke(poke(3).B)
      return poke
    } else {
      val rand = scala.util.Random
      val poke = Seq(rand.nextInt(math.pow(2, cWid).toInt), rand.nextInt(math.pow(2, nWid).toInt), rand.nextInt(math.pow(2, rWid).toInt), rand.nextInt(2))
      pokePrioID(port, poke)
    }
  }

  def pokePrioIDVec(port: Vec[PriorityID], poke: Seq[Seq[Int]] = null): Seq[Seq[Int]] = {
    if (poke != null) Seq.tabulate(port.length)(i => pokePrioID(port(i), poke(i)))
    else Seq.tabulate(port.length)(i => pokePrioID(port(i)))
  }

  def peekPrioId(port: PriorityID): Seq[Int] = {
    Seq(port.prio.c, port.prio.n, port.id, port.active).map(_.peek.litValue.toInt)
  }

  def peekPrio(port: Priority): Seq[Int] = {
    Seq(port.c, port.n).map(_.peek.litValue.toInt)
  }

  def peekPrioIdVec(port: Vec[PriorityID]): Seq[Seq[Int]] = {
    Seq.tabulate(port.length)(i => peekPrioId(port(i)))
  }

  def prioIdToString(data: Seq[Int]): String = {
    data.mkString(":")
  }

  def prioIdVecToString(data: Seq[Seq[Int]]): String = {
    data.map(_.mkString(":")).mkString(", ")
  }
}
