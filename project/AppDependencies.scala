import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val bootstrapVersion = "5.24.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-28"    % bootstrapVersion   % "test, it"
  )
}
