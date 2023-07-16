import mill._
import scalalib._

object `skunk-quill` extends SbtModule {
  def scalaVersion = "2.13.8"

  def scalacOptions = super.scalacOptions() ++ Seq("-language:experimental.macros", "-Xsource:3")

  def ivyDeps = Agg(
    ivy"org.tpolecat::skunk-core:0.6.0",
    ivy"io.getquill::quill-sql:4.6.1"
  )

  object test extends ScalaTests with TestModule.Munit {
    def ivyDeps =
      Agg(
        ivy"org.scalameta::munit::1.0.0-M8",
        //org.typelevel" %%% "munit-cats-effect" % version % "test"
        ivy"org.typelevel::munit-cats-effect:2.0.0-M1"
      )
  }
}
