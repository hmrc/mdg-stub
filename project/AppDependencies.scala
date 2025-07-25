import sbt._

object AppDependencies {

  val bootstrapVersion = "9.16.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % bootstrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30"    % bootstrapVersion   % Test
  )
}
