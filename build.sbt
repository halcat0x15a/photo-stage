lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "org.halcat",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "photo-stage",
    scalacOptions += "-deprecation",
    fork in run := true
  )
