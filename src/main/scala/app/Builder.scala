package app

import app.Consts._
import app.ScalaWrappers.{RichOptions, RichScene, RichSootClass}
import soot.options.Options
import soot.{G, Scene}

import scala.reflect.ClassTag

object Builder {

  def setup() = {
    G.reset()
    val options = Options.v()
    options.allowPhantomRefs = true
    options.wholeProgram = true
    options.processPath = instrumentsPath
    options.excludes = excludes
    options.keepLineNumber = true
    options.setPhaseOption("jb", "use-original-names:true")
    options.srcPrec = Options.src_prec_class
    Scene.v().loadNecessaryClasses()
  }

  def ofClass[T: ClassTag] = Scene.v().sootClassOpt(implicitly[ClassTag[T]].runtimeClass.getName)

  def ofMethod[T: ClassTag](name: String) = {
    val Some(clazz) = ofClass[T]
    val method      = clazz.methods.find(_.getName.contains(name)).head
    val body        = method.retrieveActiveBody()
    (method, body)
  }

  def ofMethod(className: String, name: String) = {
    val method = Scene.v().sootClassOpt(className).head.methods.find(_.getName.contains(name)).head
    val body   = method.retrieveActiveBody()
    (method, body)
  }

}
