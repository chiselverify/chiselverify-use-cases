package priorityqueue.lib

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chiseltest._
import priorityqueue.lib.BundleTypes._
import priorityqueue.lib.Helpers._
import priorityqueue.lib.Parameters._

class PriorityType(cyclic: Int, normal: Int, refId: Int, act: Boolean) {
  var c: Int = cyclic
  var n: Int = normal
  var id: Int = refId
  var active: Boolean = act

  def isLargerThan(b: PriorityType, currentSC: Int): Boolean = {
    (((c - currentSC) & (Math.pow(2, cWid).toInt - 1)) > ((b.c - currentSC) & (Math.pow(2, cWid).toInt - 1))) || (c == b.c && n > b.n)
  }

  def isSmallerThan(b: PriorityType, currentSC: Int): Boolean = {
    (((c - currentSC) & (Math.pow(2, cWid).toInt - 1)) < ((b.c - currentSC) & (Math.pow(2, cWid).toInt - 1))) || (c == b.c && n < b.n)
  }

  def ==(b: PriorityType): Boolean = {
    (c == b.c) && (n == b.n) && (id == b.id) && (active == b.active)
  }

  def !=(b: PriorityType): Boolean = {
    (c != b.c) || (n != b.n) || (id != b.id) || (active != b.active)
  }

  def activate(): Unit = active = true

  def deactivate(): Unit = active = false

  def isActive: Boolean = active

  override def toString: String = {
    s"${if (active) 1 else 0}:$c:$n:$id"
  }

  def toHWType: PriorityID = {
    (new PriorityID).Lit(_.prio -> (new Priority).Lit(_.c -> c.U, _.n -> n.U), _.id -> id.U, _.active -> active.B)
  }
}

object PriorityType {
  // create instance from concrete values
  def apply(cyclic: Int, normal: Int, refId: Int): PriorityType = {
    new PriorityType(cyclic, normal, refId, true)
  }

  // create instance from concrete values in a seq
  def apply(seq: Seq[Int]): PriorityType = {
    new PriorityType(seq.head, seq(1), seq(2), seq(3) == 1)
  }

  // create instance with random values
  def apply(): PriorityType = {
    val r = scala.util.Random
    new PriorityType(randPow2Max(cWid), randPow2Max(nWid), randPow2Max(rWid), r.nextBoolean())
  }

  // create instance from a hardware port
  def apply(port: PriorityID): PriorityType = {
    new PriorityType(port.prio.c.peek.litValue.toInt, port.prio.n.peek.litValue.toInt, port.id.peek.litValue.toInt, port.active.peek.litToBoolean)
  }
}