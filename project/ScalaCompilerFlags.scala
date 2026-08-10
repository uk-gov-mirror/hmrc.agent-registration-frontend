object ScalaCompilerFlags {

  val scalaCompilerOptions: Seq[String] = Seq(
//    "-explain",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-Wconf:msg=While parsing annotations in:silent",
    "-Yno-flexible-types",
    "-Wconf:src=target/.*:s", // silence warnings from compiled files (this includes both compiled routes and twirl templates)
//    "-source:3.7-migration", // Use Scala 3 migration mode
//    "-rewrite" // Enable rewriting
  )

  val strictScalaCompilerOptions: Seq[String] = Seq(
    "-Xfatal-warnings",
    "-Wvalue-discard",
    "-feature"
  )

}
