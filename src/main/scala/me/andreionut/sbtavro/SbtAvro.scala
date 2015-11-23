package me.andreionut.sbtavro

import java.io.File

import com.jayway.jsonpath.JsonPath
import net.minidev.json.JSONArray
import org.apache.avro.Protocol
import org.apache.avro.Schema
import org.apache.avro.compiler.idl.Idl
import org.apache.avro.compiler.specific.SpecificCompiler
import org.apache.avro.generic.GenericData.StringType

import sbt.Classpaths
import sbt.Compile
import sbt.ConfigKey.configurationToKey
import sbt.FileFunction
import sbt.FilesInfo
import sbt.Keys._
import sbt.Logger
import sbt.Plugin
import sbt.Scoped.t2ToTable2
import sbt.Setting
import sbt.SettingKey
import sbt.TaskKey
import sbt.config
import sbt.globFilter
import sbt.inConfig
import sbt.richFile
import sbt.singleFileFinder
import sbt.toGroupID

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Try

import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

/**
 * Simple plugin for generating the Java sources for Avro schemas and protocols.
 */
object SbtAvro extends Plugin {
  final val avroPrimitives = Set("null", "boolean", "int", "long", "float", "double", "bytes", "string", "record", "enum", "array", "map", "fixed")

  val avroConfig = config("avro")

  val stringType = SettingKey[String]("string-type", "Type for representing strings. " +
    "Possible values: CharSequence, String, Utf8. Default: CharSequence.")

  val fieldVisibility = SettingKey[String]("field-visibiliy", "Field Visibility for the properties" +
    "Possible values: private, public, public_deprecated. Default: public_deprecated.")

  val generate = TaskKey[Seq[File]]("generate", "Generate the Java sources for the Avro files.")

  lazy val avroSettings: Seq[Setting[_]] = inConfig(avroConfig)(Seq[Setting[_]](
    sourceDirectory <<= (sourceDirectory in Compile) { _ / "avro" },
    javaSource <<= (sourceManaged in Compile) { _ / "compiled_avro" },
    stringType := "CharSequence",
    fieldVisibility := "public_deprecated",
    version := "1.7.3",

    managedClasspath <<= (classpathTypes, update) map { (ct, report) =>
      Classpaths.managedJars(avroConfig, ct, report)
    },

    generate <<= sourceGeneratorTask)) ++ Seq[Setting[_]](
    sourceGenerators in Compile <+= (generate in avroConfig),
    managedSourceDirectories in Compile <+= (javaSource in avroConfig),
    cleanFiles <+= (javaSource in avroConfig),
    libraryDependencies <+= (version in avroConfig)("org.apache.avro" % "avro-compiler" % _),
    ivyConfigurations += avroConfig)

  private def compile(srcDir: File, target: File, log: Logger, stringTypeName: String, fieldVisibilityName: String) = {
    val stringType = StringType.valueOf(stringTypeName)
    log.info("Avro compiler using stringType=%s".format(stringType))

    val schemaParser = new Schema.Parser()

    for (idl <- (srcDir ** "*.avdl").get) {
      log.info("Compiling Avro IDL %s".format(idl))
      val parser = new Idl(idl.asFile)
      val protocol = Protocol.parse(parser.CompilationUnit.toString)
      val compiler = new SpecificCompiler(protocol)
      compiler.setStringType(stringType)
      compiler.setFieldVisibility(SpecificCompiler.FieldVisibility.valueOf(fieldVisibilityName.toUpperCase))
      compiler.compileToDestination(null, target)
    }

    for (schemaFile <- sortSchemaFiles((srcDir ** "*.avsc").get)) {
      log.info("Compiling Avro schema %s".format(schemaFile))
      val schemaAvr = schemaParser.parse(schemaFile)
      val compiler = new SpecificCompiler(schemaAvr)
      compiler.setStringType(stringType)
      compiler.compileToDestination(null, target)
    }

    for (protocol <- (srcDir ** "*.avpr").get) {
      log.info("Compiling Avro protocol %s".format(protocol))
      SpecificCompiler.compileProtocol(protocol.asFile, target)
    }

    (target ** "*.java").get.toSet
  }

  private def sourceGeneratorTask = (streams,
    sourceDirectory in avroConfig,
    javaSource in avroConfig,
    stringType,
    fieldVisibility,
    cacheDirectory) map {
      (out, srcDir, targetDir, stringTypeName, fieldVisibilityName, cache) =>
        val cachedCompile = FileFunction.cached(cache / "avro",
          inStyle = FilesInfo.lastModified,
          outStyle = FilesInfo.exists) { (in: Set[File]) =>
            compile(srcDir, targetDir, out.log, stringTypeName, fieldVisibilityName)
          }
        cachedCompile((srcDir ** "*.av*").get.toSet).toSeq
    }

  def sortSchemaFiles(files: Iterable[File]): List[File] = {
    val schemas: Iterable[SchemaDetails] = files.map(readSchema)
    val nameToSchema: mutable.Map[String, SchemaDetails] = mutable.Map()
    schemas.foreach(s => nameToSchema.put(s.name, s))
    val graph: Graph[String, DiEdge] = createDependencyGraph(schemas)
    if (graph.isAcyclic) {
      graph.topologicalSort.map(n => nameToSchema(n).file)
    } else {
      throw new Exception(s"Circular dependency found in the Avro schema files! ${graph.findCycle.get.toString}")
    }
  }

  def readSchema(file: File): SchemaDetails = {
    val json = JsonPath.parse(file)
    val name: String = json.read[String]("name")
    val namespace: String = Try(json.read[String]("namespace")).getOrElse("")
    val dependsOn: mutable.Set[String] = mutable.Set()
    val addDependency: String => Unit = { c => if (!isPrimitive(c)) {dependsOn.add(getFullName(c, namespace))} }
    json.read[JSONArray]("$.fields..type").toArray.foreach {
      case name: String => addDependency(name)
      case names: JSONArray => names.foreach(i => addDependency(i.toString))
      case map: java.util.Map[String, _] => map.get("type") match {
        case kind if Seq("array", "map").contains(kind) => addDependency(map.get("items").toString)
        case _ => None
      }
    }
    SchemaDetails(file, namespace, getFullName(name, namespace), dependsOn.toSet)
  }

  protected def isPrimitive(name: String): Boolean = avroPrimitives(name)

  protected def getFullName(name: String, namespace: String): String = if (name.contains(".")) name else namespace + "." + name

  protected def createDependencyGraph(schemas: Iterable[SchemaDetails]): Graph[String, DiEdge] = {
    @tailrec
    def createGraph(schemas: Iterable[SchemaDetails], graph: Graph[String, DiEdge]): Graph[String, DiEdge] = {
      if (schemas.isEmpty) {
        graph
      } else {
        val schema::remainingSchemas = schemas
        if (schema.dependsOn.isEmpty) {
          createGraph(remainingSchemas, graph + schema.name)
        } else {
          val nodes = schema.dependsOn.map{ name => name ~> schema.name }
          createGraph(remainingSchemas, addNodes(graph, nodes))
        }
      }
    }
    createGraph(schemas, Graph())
  }

  @tailrec
  protected def addNodes(graph: Graph[String, DiEdge], nodes: Set[DiEdge[String]]): Graph[String, DiEdge] = {
    if (nodes.isEmpty) {
      graph
    } else {
      addNodes(graph + nodes.head, nodes.tail)
    }
  }
}

case class SchemaDetails(file: File, namespace: String, name: String, dependsOn: Set[String])
