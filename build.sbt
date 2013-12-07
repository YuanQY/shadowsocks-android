libraryDependencies ++= Seq(
  "com.google.android" % "support-v4" % "r12",
  "com.google.android" % "analytics" % "3.01",
  "com.google.android" % "admob" % "6.4.1",
  "dnsjava" % "dnsjava" % "2.1.5",
  "org.scalaj" %% "scalaj-http" % "0.3.10",
  "commons-net" % "commons-net" % "3.3",
  "com.google.zxing" % "android-integration" % "2.2"
)

libraryDependencies ++= Seq(
  "de.keyboardsurfer.android.widget" % "crouton" % "1.8.1",
  "com.nostra13.universalimageloader" % "universal-image-loader" % "1.8.4",
  "com.j256.ormlite" % "ormlite-core" % "4.47",
  "com.j256.ormlite" % "ormlite-android" % "4.47"
)

libraryDependencies ++= Seq(
  "com.github.gabrielemariotti.cards" % "library" % "1.0.0",
  "net.simonvt.menudrawer" % "menudrawer" % "3.0.4" artifacts(Artifact("menudrawer", "aar", "aar"))
)


