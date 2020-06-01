import sbt.{ForkOptions, TestDefinition}
import sbt.Tests.{Group, SubProcess}

object TestPhases {
  def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
    tests map { test =>
      val forkOptions = ForkOptions().withRunJVMOptions(Vector("-Dtest.name=" + test.name))
      Group(test.name, Seq(test), SubProcess(config = forkOptions))
    }
}
