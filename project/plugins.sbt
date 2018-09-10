resolvers += "SBT release"  at "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"

resolvers += Resolver.jcenterRepo

libraryDependencies += "com.amazonaws" % "aws-java-sdk-lambda" % "1.11.405"

addSbtPlugin("com.github.tptodorov" % "sbt-cloudformation" % "0.2.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")

