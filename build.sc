import mill._
import scalalib._
import publish._

object `skunk-quill` extends SbtModule with PublishModule {
  def scalaVersion = "2.13.8"

  def scalacOptions = super.scalacOptions() ++ Seq("-language:experimental.macros", "-Xsource:3")

  def ivyDeps = Agg(
    ivy"org.tpolecat::skunk-core:0.6.0",
    ivy"io.getquill::quill-sql:4.6.1"
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"org.typelevel:::kind-projector:0.13.2"
  )

  object test extends ScalaTests with TestModule.Munit {
    def ivyDeps =
      Agg(
        ivy"org.scalameta::munit::1.0.0-M8",
        //org.typelevel" %%% "munit-cats-effect" % version % "test"
        ivy"org.typelevel::munit-cats-effect:2.0.0-M1"
      )
  }

  def publishVersion = "0.1.0"

  def pomSettings = PomSettings(
    description = "A library for using Skunk with Quill",
    organization = "com.ivmoreau",
    url = "https://github.com/ivanmoreau/skunk-quill.scala",
    licenses = Seq(License.`MPL-2.0`),
    versionControl = VersionControl.github("ivanmoreau", "skunk-quill.scala"),
    developers = Seq(
      Developer("ivanmoreau", "Iván Molina Rebolledo", "http://github.com/ivanmoreau")
    )
  )
}
