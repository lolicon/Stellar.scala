package app

import app.solver.IntraProceduralSolver
import sample.ziwu.Instrumented

object Application {
  def main(args: Array[String]): Unit = {
    Builder.initialize()
    IntraProceduralSolver[Instrumented]("entry").run()
  }
}
