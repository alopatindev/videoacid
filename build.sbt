android.Plugin.androidBuild

name := "videoacid"
version := "0.0.1"

scalaVersion := "2.11.7"
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
scalacOptions in Compile ++= Seq("-feature")

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.android.support" % "support-v4" % "20.0.0"
)

// Repositories for dependencies
resolvers ++= Seq(
  Resolver.mavenLocal,
  DefaultMavenRepository,
  Resolver.typesafeRepo("releases"),
  Resolver.typesafeRepo("snapshots"),
  Resolver.typesafeIvyRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.defaultLocal,
  "jcenter" at "http://jcenter.bintray.com"
)

platformTarget in Android := "android-15"
run <<= run in Android
proguardScala in Android := true
useProguard in Android := true
proguardOptions in Android ++= Seq(
  "-ignorewarnings",
  "-keep class scala.Dynamic"
)
