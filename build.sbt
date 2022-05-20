import NativePackagerHelper._
import RpmConstants._
import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker._
name := "pluto-core"

version := "1.0-dev"

//don't use RUNNING_PID file as that causes problems when we switch UIDs in Docker
//https://stackoverflow.com/questions/28351405/restarting-play-application-docker-container-results-in-this-application-is-alr
javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null"
)

scalacOptions ++= Seq("-deprecation", "-feature")

val circeVersion = "0.12.3"

lazy val `pluto-core` = (project in file("."))
  .enablePlugins(PlayScala) //NOTE don't enable AshScriptPlugin because that breaks the backup_launcher script
    .settings(
      version := sys.props.getOrElse("build.number","DEV"),
      dockerExposedPorts := Seq(9000),
      dockerUsername  := sys.props.get("docker.username"),
      dockerRepository := Some("guardianmultimedia"),
      packageName in Docker := "guardianmultimedia/pluto-core",
      packageName := "pluto-core",
      dockerBaseImage := "docker.io/openjdk:8u292-jre-slim-buster",
      dockerPermissionStrategy := DockerPermissionStrategy.CopyChown,
      dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"pluto-core",Some(sys.props.getOrElse("build.number","DEV"))),
      scalacOptions ++= Seq("-deprecation", "-feature"),
      dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "apt-get","-y", "update", "&&", "apt-get", "-y", "install", "libxml2-utils", "&&",
          "apt-get", "-y", "autoclean", "&&", "apt-get", "-y", "clean", "&&", "rm", "-rf", "/var/cache/apt" ),
        Cmd("RUN", "mv", "/opt/docker/conf/docker-application.conf", "/opt/docker/conf/application.conf"),
        Cmd("RUN", "mkdir", "-p", "/data", "&&", "chown", "demiourgos728", "/data"),
        Cmd("RUN", "chmod", "a+rx", "/opt/docker/bin/pluto-core"),
        Cmd("USER", "demiourgos728"),
      )
    )

javaOptions in Test += "-Duser.timezone=UTC"

scalaVersion := "2.13.2"

libraryDependencies ++= Seq( jdbc, ehcache , ws   , specs2 % Test, guice )

libraryDependencies += evolutions

testOptions in Test ++= Seq( Tests.Argument("junitxml", "junit.outdir", sys.env.getOrElse("SBT_JUNIT_OUTPUT","/tmp")), Tests.Argument("console") )

PlayKeys.devSettings := Seq("play.akka.dev-mode.akka.http.server.request-timeout"->"120 seconds")

unmanagedResourceDirectories in Test +=  (baseDirectory ( _ /"target/web/public/test" )).value

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.13.jre6",
  // https://mvnrepository.com/artifact/com.typesafe.play/play-slick
  "com.typesafe.play" %% "play-slick" % "4.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.2",
  "commons-io" % "commons-io" % "2.6",
  // https://mvnrepository.com/artifact/com.typesafe.play/play-json-joda
  "com.typesafe.play" %% "play-json-joda" % "2.7.4",
  "commons-codec" % "commons-codec" % "1.13",
  "com.github.scopt" %% "scopt" % "4.0.1"
)
// https://mvnrepository.com/artifact/com.typesafe.slick/slick
libraryDependencies += "com.typesafe.slick" %% "slick" % "3.3.2"


//authentication
libraryDependencies ++= Seq(
  "com.unboundid" % "unboundid-ldapsdk" % "5.0.0",
  "com.nimbusds" % "nimbus-jose-jwt" % "8.21",
)


//nice json parsing
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

// upgrade guava
// https://mvnrepository.com/artifact/com.google.guava/guava
libraryDependencies += "com.google.guava" % "guava" % "30.1-jre"

val akkaManagementVersion = "1.0.8"
val akkaVersion = "2.6.14"
//messaging persistence and clustering
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.3",
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.lightbend.akka" %% "akka-stream-alpakka-xml" % "3.0.4",
)

//explicit akka upgrades for version fixes
val akkaHttpVersion = "10.1.12"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
)

//Sentry
libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.30"

//Reflections library for scanning classpath
libraryDependencies += "org.reflections" % "reflections" % "0.9.11"

libraryDependencies += "com.newmotion" %% "akka-rabbitmq" % "5.1.2"

libraryDependencies += "org.mockito" % "mockito-inline" % "2.8.9"

enablePlugins(UniversalPlugin)

enablePlugins(LinuxPlugin)

enablePlugins(RpmPlugin, JavaServerAppPackaging, SystemdPlugin, DockerPlugin)

//Generic Linux package build configuration
mappings in Universal ++= directory("postrun/")

packageSummary in Linux := "A system to manage, backup and archive multimedia project files"

packageDescription in Linux := "A system to manage, backup and archive multimedia project files"

//RPM build configuration
rpmVendor := "Andy Gallagher <andy.gallagher@theguardian.com>"

rpmUrl := Some("https://github/fredex42/projectlocker")

rpmRequirements := Seq("libxml2", "gzip")

serverLoading in Universal := Some(ServerLoader.Systemd)

packageName in Rpm := "projectlocker"

version in Rpm := "1.0"

rpmRelease := sys.props.getOrElse("build.number","DEV")

packageArchitecture := "noarch"

rpmLicense := Some("custom")

maintainerScripts in Rpm := Map(
  Post -> Seq("cp -f /usr/share/projectlocker/conf/sudo-trapdoor /etc/sudoers.d/projectlocker")
)

