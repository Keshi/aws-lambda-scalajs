import java.io.FileInputStream
import java.nio.ByteBuffer

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.{AWSLambdaClient, model}
import com.amazonaws.services.lambda.model._
import com.amazonaws.util.IOUtils
import com.github.tptodorov.sbt.cloudformation.CloudFormation
import com.github.tptodorov.sbt.cloudformation.Import.Keys._
import com.github.tptodorov.sbt.cloudformation.Import.Configurations._

CloudFormation.defaultSettings

val zipJS = taskKey[File]("build zip file containing all generated JS and exporter")
val lambdaClient = taskKey[AWSLambdaClient]("AWSLambdaClient")
val uploadFunctions = taskKey[Unit]("upload lambda function")
val exportedFunctions = taskKey[Seq[String]]("exported functions")
val lambdaExecRole = taskKey[String]("lambda exec role")

zipJS in Compile <<= (fastOptJS in Compile, packageJSDependencies in Compile, target) map {
  (jsFile, depsFile, tf) =>

    val zipFile = tf / "lambda.zip"
    val inputs: Seq[(File, String)] = Seq((depsFile, "index.js")) ++ (Seq(jsFile.data) x Path.flat)

    IO.zip(inputs, zipFile)

    zipFile
}

exportedFunctions <<= (packageJSDependencies in Compile) map {
  (exportsFile) =>
    val export = "exports\\.(\\w+)\\W*=".r

    export.findAllIn(IO.read(exportsFile)).matchData.map(_.group(1)).toList
}

def makeLambdaConfiguration(config: Configuration) = Seq(

  lambdaClient in config <<= (awsCredentials in config, stackRegion in config) map {
    (aws, region) =>
      val client = new AWSLambdaClient(aws)
      client.setRegion(Region.getRegion(Regions.fromName(region)))
      client
  },

  uploadFunctions in config <<= (name, zipJS in Compile, lambdaClient in config, exportedFunctions, lambdaExecRole in config, streams in config) map {
    (n, zipFile, client, lambdaHandlers, execRole, s) =>
      lambdaHandlers.foreach {
        handler =>
          val bytes = ByteBuffer.wrap(IOUtils.toByteArray(new FileInputStream(zipFile)))
          val functionName = s"$n-$handler-$config"

          try {
            val funcResult = client.getFunction(new GetFunctionRequest().withFunctionName(functionName))
            val result2 = client.updateFunctionCode(new UpdateFunctionCodeRequest()
              .withFunctionName(functionName)
              .withZipFile(bytes))
            s.log.info(s"updated lambda function '$handler' : $result2")
          } catch {
            case e:ResourceNotFoundException =>
              val result = client.createFunction(new CreateFunctionRequest()
                .withFunctionName(functionName)
                .withHandler(s"index.$handler")
                .withCode(new FunctionCode().withZipFile(bytes))
                .withRuntime(model.Runtime.Nodejs810)
                .withRole(execRole))
              s.log.info(s"created lambda function '$handler' : $result")
          }
      }
  }

)

// Settings specific for this project

stackRegion := "us-west-1"

enablePlugins(ScalaJSPlugin)

name := "aws-lambda-scalajs"

scalaVersion := "2.12.6"

// for running under node
scalaJSStage in Global := FastOptStage

//persistLauncher in Compile := true
skip in packageJSDependencies := false

jsDependencies += ProvidedJS / "lambda-exports.js"

lambdaExecRole := "arn:aws:iam::520631736852:role/lambda_exec_role"

makeLambdaConfiguration(Staging)

