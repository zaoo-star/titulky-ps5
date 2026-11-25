// Místní kontrola bez Android SDK: přeloží zdrojáky aplikace a pustí unit testy.
//
//   cd ci/mistni-kontrola && gradle test
//
// Android SDK ani knihovny Google se do cloudového sezení stáhnout nedají, proto se překládá proti
// knihovně Androidu z Maven Central (Robolectric) a proti náhradám ve složce stub/.
// Odhalí překlepy a chyby v typech dřív než dvacetiminutová zkouška na GitHubu. Nenahrazuje ji:
// nezná zdroje (res/), manifest, nativní část ani to, od které verze Androidu která funkce existuje.
plugins { kotlin("jvm") version "2.0.21" }
repositories { mavenCentral() }

val app = rootDir.resolve("../../app/src")

// Xbox (XboxLink.kt): WebRTC je knihovna pro Android (.aar) – k překladu stačí její classes.jar.
val webrtcAar by configurations.creating { isTransitive = false }
val webrtcJar = layout.buildDirectory.file("webrtc/classes.jar")
val unpackWebrtc by tasks.registering(Copy::class) {
    from(zipTree(webrtcAar.singleFile)) { include("classes.jar") }
    into(layout.buildDirectory.dir("webrtc"))
}
dependencies {
    webrtcAar("io.getstream:stream-webrtc-android:1.3.7@aar")
    compileOnly(files(webrtcJar).builtBy(unpackWebrtc))
    compileOnly("org.robolectric:android-all:15-robolectric-12650502")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1") { isTransitive = false }
    compileOnly("com.google.zxing:core:3.5.3")
    // instalace do televize LG (LgInstaller.kt)
    compileOnly("com.github.mwiede:jsch:2.28.8")
    // nahrání do Android TV přes ADB (AtvInstaller.kt); v testu AtvInstallTest i jako vzor klíče ADB
    compileOnly("dev.mobile:dadb:2.0.0")
    testImplementation("dev.mobile:dadb:2.0.0")
    testImplementation("junit:junit:4.13.2")
}
kotlin {
    sourceSets["main"].kotlin.srcDirs(app.resolve("main/java"), "stub")
    sourceSets["test"].kotlin.srcDirs(app.resolve("test/java"))
}
// Knihovna z Robolectricu nese poznámky o null, které skutečné SDK překladači neukazuje.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll("-Xnullability-annotations=@android.annotation:ignore", "-Xnullability-annotations=@androidx.annotation:ignore")
}
tasks.test {
    useJUnit(); testLogging { events("failed") }
    // -Ppruvodce: PruvodceTest zapíše pomoc/pruvodce-data.js ze šablony core/Pruvodce.kt
    if (project.hasProperty("pruvodce")) { environment("PRUVODCE_ZAPSAT", "1"); outputs.upToDateWhen { false } }
}
