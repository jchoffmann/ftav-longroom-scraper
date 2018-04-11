lazy val root = project
  .dependsOn(RootProject(uri("git://github.com/a8m/pb-scala.git")))
  .in(file("."))
  .settings(Seq(
    name := "ftalphaville_longroom",
    version := "0.1",
    scalaVersion := "2.11.4", // Version required for pb-scala
    libraryDependencies ++= Seq(
      "org.jsoup"              % "jsoup"        % "1.11.2",
      "com.evernote"           % "evernote-api" % "1.25.1",
      "org.scala-lang.modules" %% "scala-xml"   % "1.1.0",
      "com.google.guava"       % "guava"        % "24.1-jre",
      "com.typesafe.akka"      %% "akka-actor"  % "2.5.11"
//      "com.github.scopt"       %% "scopt"       % "3.7.0"
    )
  ))
