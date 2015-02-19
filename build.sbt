name := "finagle-redis-sample"

version := "1.0.0"

scalaVersion  := "2.10.4"

resolvers ++= Seq(
  "Twitter repository" at "http://maven.twttr.com"
)

libraryDependencies ++= Seq(
    "com.twitter"   %% "finagle-redis"    % "6.24.0"
)
