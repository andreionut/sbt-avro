package me.andreionut.sbtavro

import java.io.File

import org.apache.avro.Schema
import org.apache.avro.compiler.specific.SpecificCompiler
import org.apache.avro.generic.GenericData.StringType
import org.specs2.mutable.Specification

import scala.util.Random

/**
 * Created by jeromewacongne on 06/08/2015.
 */
class SbtAvroSpec extends Specification {
  val sourceDir = new File(getClass.getClassLoader.getResource("avro").toURI)
  val targetDir = new File(sourceDir.getParentFile, "generated")
  val sourceFiles = Seq(
    new File(sourceDir, "b.avsc"),
    new File(sourceDir, "f.avsc"),
    new File(sourceDir, "c.avsc"),
    new File(sourceDir, "d.avsc")
  )

  "It should be possible to compile types depending on others" >> {
    sourceFiles.permutations.foreach(testOrdering)
    true must beTrue
  }

  def testOrdering(randomSourceFiles: Seq[File]): Unit = {
    val parser = new Schema.Parser()
    val packageDir = new File(targetDir, "me/andreionut/sbtavro")
    val aJavaFile = new File(packageDir, "F.java")
    val bJavaFile = new File(packageDir, "B.java")
    val cJavaFile = new File(packageDir, "C.java")
    val dJavaFile = new File(packageDir, "D.java")
    aJavaFile.delete()
    bJavaFile.delete()
    cJavaFile.delete()
    dJavaFile.delete()

    for(schemaFile <- SbtAvro.sortSchemaFiles(randomSourceFiles)) {
      val schemaAvr = parser.parse(schemaFile)
      val compiler = new SpecificCompiler(schemaAvr)
      compiler.setStringType(StringType.CharSequence)
      compiler.compileToDestination(null, targetDir)
    }

    aJavaFile.isFile must beTrue
    bJavaFile.isFile must beTrue
    cJavaFile.isFile must beTrue
    dJavaFile.isFile must beTrue
  }
}
