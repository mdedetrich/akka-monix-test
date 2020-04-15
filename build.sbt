val monixVersion      = "3.2.0-4524fe2-SNAPSHOT"
val akkaHttpVersion   = "10.1.11"
val akkaStreamVersion = "2.6.3"

resolvers += Resolver.mavenLocal
resolvers in ThisBuild += Resolver.sonatypeRepo("snapshots")

fork := true

libraryDependencies := Seq(
  "com.typesafe.akka"          %% "akka-http"      % akkaHttpVersion,
  "com.typesafe.akka"          %% "akka-stream"    % akkaStreamVersion,
  "io.monix"                   %% "monix"          % monixVersion,
  "org.mdedetrich"             %% "monix-mdc"      % "0.1.0-SNAPSHOT",
  "ch.qos.logback"             % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2"
)
