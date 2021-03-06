import com.timushev.sbt.updates.UpdatesPlugin.autoImport.dependencyUpdatesFilter
import sbt.moduleFilter
import sbt.{ModuleID, _}

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.10"
lazy val supportedScalaVersions = List(scala212, scala211)

// factor out common settings
ThisBuild / scalacOptions += "-target:jvm-1.8"

ThisBuild / organization := "com.datastax.dse"
ThisBuild / publishMavenStyle := true
ThisBuild / pomExtra := Publishing.License ++ Publishing.ExtraPom
ThisBuild / publishTo := Publishing.Repository
ThisBuild / credentials ++= Publishing.Creds
ThisBuild / version := Publishing.Version

Global / resolvers ++= Seq(
  DefaultMavenRepository,
  Resolver.sonatypeRepo("public")
)

lazy val IntegrationTest = config("it") extend Test

lazy val integrationTestsWithFixtures = taskKey[Map[TestDefinition, Seq[String]]]("Evaluates names of all " +
  "Fixtures sub-traits for each test. Sets of fixture sub-traits names are used to form group tests.")

lazy val commonSettings = Seq(
  // dependency updates check
  dependencyUpdatesFailBuild := true,
  dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang" | "org.eclipse.jetty"),
  fork := true,
  parallelExecution := true,
  testForkedParallel := false,
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
)

val annotationProcessor = Seq(
  "-processor", "com.datastax.oss.driver.internal.mapper.processor.MapperProcessor"
)

lazy val root = (project in file("."))
  .aggregate(connector, testSupport, driver)
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true
  )

lazy val connector = (project in file("connector"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*) //This and above enables the "it" suite
  .settings(commonSettings)
  .settings(
    crossScalaVersions := supportedScalaVersions,

    // set the name of the project
    name := "spark-cassandra-connector",

    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

    // test grouping
    integrationTestsWithFixtures := {
      Testing.testsWithFixtures((testLoader in IntegrationTest).value, (definedTests in IntegrationTest).value)
    },

    IntegrationTest / testGrouping := Testing.makeTestGroups(integrationTestsWithFixtures.value),
    IntegrationTest / testOptions += Tests.Argument("-oF"),  // show full stack traces

    Test / javacOptions ++= annotationProcessor ++ Seq("-d", (classDirectory in Test).value.toString),

    Global / concurrentRestrictions := Seq(Tags.limitAll(Testing.parallelTasks)),

    libraryDependencies ++= Dependencies.Spark.dependencies
      ++ Dependencies.TestConnector.dependencies
      ++ Dependencies.Jetty.dependencies
  )
  .dependsOn(
    testSupport % "test",
    driver
  )

lazy val testSupport = (project in file("test-support"))
  .settings(commonSettings)
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "spark-cassandra-connector-test-support",
    libraryDependencies ++= Dependencies.TestSupport.dependencies
  )

lazy val driver = (project in file("driver"))
  .settings(commonSettings)
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "spark-cassandra-connector-driver",
    libraryDependencies ++= Dependencies.Driver.dependencies
      ++ Dependencies.TestDriver.dependencies
      :+ ("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  )
