// Společná logika ReCaptionu (app/src/main/java/cz/recaption/core) přeložená pro iPhone (Kotlin/Native).
// Soubory z Androidu se berou beze změny; co v nich je jen pro Javu, nahrazují soubory v src/nahrady.
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { kotlin("multiplatform") version "2.0.21" }

val jadro = rootDir.resolve("../../app/src/main/java/cz/recaption/core")
val testy = rootDir.resolve("../../app/src/test/java/cz/recaption/core")

kotlin {
    // Linux jen pro překlad a testy bez Macu (cloudové sezení); pro iPhone framework Jadro (statický) do Xcode
    linuxX64()
    macosArm64() // testy z Androidu na Macu (GitHub)
    val xcf = XCFramework("Jadro")
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Jadro"
            isStatic = true
            xcf.add(this)
        }
    }
    sourceSets {
        all { languageSettings.optIn("kotlin.ExperimentalStdlibApi"); languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi"); languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi") }
        val commonMain by getting {
            kotlin.srcDir(jadro)
            kotlin.srcDir("src/nahrady")
            kotlin.srcDir("src/most")
            // síť, soubory a balíčky pro TV – iPhone je má po svém (Swift)
            kotlin.exclude(
                "TvSender.kt",
                "LoginServer.kt",
                "LogShare.kt",
                "SnapBatches.kt",
                "LgIpk.kt",
                "SamsungWgt.kt",
                "TvLog.kt",
                "AtvInstall.kt",
            )
        }
        val commonTest by getting {
            kotlin.srcDir(testy)
            kotlin.srcDir("src/testy")
            // testy síťových a souborových částí a zpráv do TV (TvSender) – na iPhonu zatím ne
            kotlin.exclude(
                "AtvInstallTest.kt",
                "DeeplTest.kt",
                "GameListJsonTest.kt",
                "LgInstallTest.kt",
                "LogShareTest.kt",
                "ObjectiveColorsTest.kt",
                "ObjectiveTest.kt",
                "PhoneLoginTest.kt",
                "PhoneScreenTest.kt",
                "SamsungTest.kt",
                "SnapBatchesTest.kt",
                "SpeakersAndChoicesTest.kt",
                "TvLookTest.kt",
                "TvPhoneMessageTest.kt",
                "TvSenderTest.kt",
                "TvSettingsTest.kt",
                "ReportUploadTest.kt",
            )
            dependencies { implementation(kotlin("test")) }
        }
    }
}
