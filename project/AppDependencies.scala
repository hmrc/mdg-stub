import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-play-26" % "1.8.0",
    "org.scala-lang.modules" %% "scala-xml"         % "1.1.0"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "3.9.0-play-26" % scope,
    "org.scalatest"          %% "scalatest"          % "3.0.8" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % scope,
    "org.pegdown"             % "pegdown"            % "1.6.0" % scope,

    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % scope
  )
}
