val scala3Version = "3.8.3"
val commonmarkVersion = "0.28.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "sibgen",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.commonmark" % "commonmark"                       % commonmarkVersion,
      "org.commonmark" % "commonmark-ext-gfm-tables"        % commonmarkVersion,
      "org.commonmark" % "commonmark-ext-gfm-strikethrough" % commonmarkVersion,
      "org.commonmark" % "commonmark-ext-task-list-items"   % commonmarkVersion,
      "org.commonmark" % "commonmark-ext-footnotes"         % commonmarkVersion,
      "org.commonmark" % "commonmark-ext-autolink"          % commonmarkVersion,
      "org.scala-lang" %% "scala3-compiler" % scala3Version,
      "org.scalameta" %% "munit" % "1.3.0" % Test
    )
  )
