inThisBuild(
  List(
    scalaVersion       := "2.13.13",
    crossScalaVersions := Seq("2.13.13", "3.3.3"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:implicitConversions",
      "-unchecked",
      "-language:higherKinds",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ywarn-unused",
      "-Xsource:3"
    ),
    githubWorkflowJavaVersions := List(
      JavaSpec.temurin("17"),
      JavaSpec.temurin("21")
    ),
    githubWorkflowBuild := Seq(
      WorkflowStep.Sbt(
        name = Option("Build & Test"),
        commands = List("clean", "test"),
        cond = None,
        env = Map.empty
      )
    ),
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches := Seq(
      RefPredicate.StartsWith(Ref.Tag("v")),
      RefPredicate.Equals(Ref.Branch("main"))
    ),
    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        commands = List("ci-release"),
        name = Some("Publish project"),
        env = Map(
          "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
        )
      )
    ),
    testFrameworks ++= Seq(),
    semanticdbEnabled      := true,
    versionScheme          := Some("early-semver"),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeProfileName    := "io.kaizen-solutions",
    sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
    licenses               := List("MPL-2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0/")),
    organization           := "io.kaizen-solutions",
    organizationName       := "kaizen-solutions",
    homepage               := Some(url("https://www.kaizen-solutions.io/")),
    developers := List(
      Developer("calvinlfer", "Calvin Fernandes", "cal@kaizen-solutions.io", url("https://www.kaizen-solutions.io"))
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org"
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "zio-newrelic-events",
    libraryDependencies ++= Seq(
      "dev.zio"                       %% "zio" % "2.1.1",
      "com.softwaremill.sttp.client3" %% "zio" % "3.9.7"
    )
  )
