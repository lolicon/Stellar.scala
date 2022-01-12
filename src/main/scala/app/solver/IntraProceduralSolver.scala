package app.solver

import app.ScalaWrappers.{RichBody, RichChain, RichHost, SAssignStmt, SInstanceFieldRef, SLocal, SNewExpr}
import app._
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef.EdgeAssoc
import scalax.collection.mutable.Graph

import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag

case class IntraProceduralSolver[T: ClassTag](methodName: String) {
  type PointerFlowGraph            = Graph[Pointer, DiEdge]
  type MutablePointerAllocationMap = mutable.Map[Pointer, mutable.Set[Allocation]]

  val env         = mutable.Map[Pointer, mutable.Set[Allocation]]().withDefaultValue(mutable.Set[Allocation]())
  val graph       = Graph[Pointer, DiEdge]()
  val (m, bodies) = Builder.ofMethod[T](methodName)
  val scopeName   = m.getDeclaringClass.getName
  val worklist    = mutable.Queue[(Pointer, mutable.Set[Allocation])]()

  def connect(from: Pointer, to: Pointer) = if ((graph find from ~> to).isEmpty) {
    graph.add(from ~> to)
    Option(env(from)).filter(_.nonEmpty).foreach { it => worklist += ((to, it)) }
  }

  def propagate(pointer: Pointer, delta: mutable.Set[Allocation]) = if (delta.nonEmpty) {
    env.getOrElseUpdate(pointer, mutable.Set()).addAll(delta)
    for (node <- graph.find(pointer)) {
      node.diSuccessors.foreach { node =>
        worklist += ((node.value, delta))
      }
    }
  }

  def solve() = {
    val (stores, loads) = bodies.units.foldLeft(immutable.Set[(VarField, VarPointer)](), immutable.Set[(VarPointer, VarField)]()) { (acc, ele) =>
      val (stores, loads) = acc
      ele match {
        // x.foo = y
        case SAssignStmt(SInstanceFieldRef(SLocal(self, _), field), SLocal(name, _)) =>
          (stores + ((VarField(VarPointer(methodName, self, scopeName), field.getName), VarPointer(methodName, name, scopeName))), loads)
        // y = x.foo
        case SAssignStmt(SLocal(name, _), SInstanceFieldRef(SLocal(self, _), field)) =>
          (stores, loads + ((VarPointer(methodName, name, scopeName), VarField(VarPointer(methodName, self, scopeName), field.getName))))
        case _ => acc
      }
    }

    // allocate
    worklist.addAll(bodies.units.foldLeft(mutable.Map[Pointer, mutable.Set[Allocation]]().withDefaultValue(mutable.Set[Allocation]())) { (acc, ele) =>
      ele match {
        case SAssignStmt(SLocal(allocated, _), SNewExpr(baseType)) =>
          acc.getOrElseUpdate(VarPointer(methodName, allocated, scopeName), mutable.Set()).add(Allocation(ele.lineNumber, baseType.toString))
        case _ => ()
      }
      acc
    })

    // assign
    bodies.units.foreach {
      case SAssignStmt(SLocal(left, _), SLocal(right, _)) =>
        connect(VarPointer(methodName, right, scopeName), VarPointer(methodName, left, scopeName))
      case _ => ()
    }

    var work = worklist.removeHeadOption()
    while (work.nonEmpty) {
      val Some(pointer -> allocation) = work
      val delta                       = allocation -- env(pointer)
      propagate(pointer, delta)

      pointer match {
        case variable: VarPointer =>
          delta.foreach { delta =>
            stores.filter(_._1.receiver == variable).foreach { store => connect(store._2, FieldPointer(delta, store._1.field)) }
            loads.filter(_._2.receiver == variable).foreach { load => connect(FieldPointer(delta, load._2.field), load._1) }
          }
        case _ => ()
      }
      work = worklist.removeHeadOption()
    }

  }
}
