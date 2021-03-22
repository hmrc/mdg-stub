import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28" % "4.1.0",
    "org.scala-lang.modules" %% "scala-xml"                 % "1.3.0"
  )

  val test = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"             % s"$Test,$IntegrationTest",
    "com.vladsch.flexmark"    % "flexmark-all"       % "0.35.10"           % s"$Test,$IntegrationTest",
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % s"$Test,$IntegrationTest"
  )

}
