package app

import app.ScalaWrappers.{RichBody, RichSootMethod}
import playground.samples.Prototype
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL.WithDouble._
import playground.events.Instrumented
import playground.susi.Susi

import java.io.FileWriter

object Application {
  def main(args: Array[String]): Unit = {
    Builder.setup()

//    val (main, _)   = Builder.ofMethod[Animal]("main")
//    val destination = "animal"

//    val (main, _)   = Builder.ofMethod[Instrumented]("entry")
//    val destination = "instrumented"

    val (main, _)   = Builder.ofMethod[Susi]("main")
    val destination = "animal"

//    val (foo, _) = Builder.ofMethod("playground.samples.Stage", "foo")
//    println(foo.body.sources)

    val solver = Solver(main)
    solver.solve()
    val graphviz = solver.visualizer.dump()
    val output   = compact(render(graphviz.toList))
    val writer   = new FileWriter(s"${Consts.outputDir}/${destination}.json")
    val latest   = new FileWriter(s"${Consts.outputDir}/latest.json")

    writer.write(output)
    writer.close()

    latest.write(output)
    latest.close()
  }
}
