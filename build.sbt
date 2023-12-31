// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.2" // your current series x.y

ThisBuild / organization := "com.ivmoreau"
ThisBuild / organizationName := "Iván Molina Rebolledo"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(
  "MPL-2.0" -> url("https://www.mozilla.org/media/MPL/2.0/index.f75d2927d3c1.txt")
)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ivanmoreau", "Iván Molina Rebolledo")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

val Scala213 = "2.13.8"
ThisBuild / crossScalaVersions := Seq(Scala213)
ThisBuild / scalaVersion := Scala213 // the default Scala
ThisBuild / scalacOptions ++= Seq("-Xsource:3")

// ThisBuild / tlCiScalafmtCheck.withRank(KeyRanks.Invisible) := true
// ThisBuild / tlCiHeaderCheck.withRank(KeyRanks.Invisible) := false

ThisBuild / tlFatalWarnings := false

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Run(
    name = Some("Compile"),
    commands = List("sbt compile")
  )
)

Test / parallelExecution := false

lazy val root = tlCrossRootProject.aggregate(`bird-io`)

// A wannabe Twitter Future based IO monad with support for Async
lazy val `bird-io` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("bird-io"))
  .settings(
    name := "bird-io",
    libraryDependencies ++= Seq(
      // Twitter Future is in Core Utils
      "com.twitter" %% "util-core" % "22.12.0",
      // Cats Effect 3 :)
      "org.typelevel" %%% "cats-effect" % "3.5.1",
      // Test dependencies
      "org.scalameta" %%% "munit" % "1.0.0-M8" % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.0.0-M3" % Test,
      "org.typelevel" %% "cats-effect-laws" % "3.5.1" % Test,
      "org.typelevel" %% "cats-effect-testkit" % "3.5.1" % Test,
      "org.typelevel" %% "discipline-munit" % "2.0.0-M3" % Test
    )
  )
