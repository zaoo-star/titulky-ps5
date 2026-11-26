plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Sestavení na PC s Windows (sestavit-na-pc.ps1): nativní část se tam přeložit nedá (OpenSSL chce
// linuxové nástroje), proto se vezme hotová z vydaného APK. -PnativniZ=<složka s <abi>/*.so>.
// Na GitHubu se nepoužívá, tam se nativní část překládá vždy.
val nativniZ: String? = (project.findProperty("nativniZ") as String?)?.takeIf { it.isNotBlank() }

android {
    namespace = "cz.recaption"
    compileSdk = 35

    defaultConfig {
        // Titulky PS5 (úprava ReCaptionu): vlastní ID – instaluje se vedle ReCaptionu, aktualizace z vlastního repa
        applicationId = "cz.titulkyps5"
        minSdk = 26          // Android 8 a novější (prakticky všechny Android TV a boxy)
        targetSdk = 34
        versionCode = 326
        versionName = "1.3-z9"

        // kanál APK (od 1.00-rc7, přání majitele – zkušební verze jen pro testery): „zkusebni“ = testerské APK z větví
        // (build.yml -Pkanal=zkusebni), jinak „latest“ (main, zaloha-…, místní sestavení). Čte Tester.kt z manifestu.
        manifestPlaceholders["kanal"] = if ((project.findProperty("kanal") as String?) == "zkusebni") "zkusebni" else "latest"

        // televize a boxy mají procesory ARM; emulátor při zkoušce x86 (-Pabis=x86)
        val abis = ((project.findProperty("abis") as String?) ?: "armeabi-v7a,arm64-v8a").split(",")
        // s -PabiApk (vydání na GitHubu) určují procesory dělené APK níž – abiFilters by se s nimi tloukly
        if (!project.hasProperty("abiApk")) ndk { abiFilters += abis }

        // spojení s PlayStationem: otevřená knihovna chiaki-ng (third_party/chiaki-ng)
        if (nativniZ == null) externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCHIAKI_ENABLE_TESTS=OFF", "-DCHIAKI_ENABLE_CLI=OFF", "-DCHIAKI_ENABLE_GUI=OFF",
                    "-DCHIAKI_ENABLE_SETSU=OFF", "-DCHIAKI_ENABLE_ANDROID=ON",
                    "-DCHIAKI_ENABLE_FFMPEG_DECODER=OFF", "-DCHIAKI_ENABLE_PI_DECODER=OFF",
                    "-DCHIAKI_LIB_ENABLE_OPUS=OFF", "-DCHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT=ON",
                    "-DCHIAKI_LIB_JSONC_EXTERNAL_PROJECT=ON", "-DCHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT=ON",
                    "-DCHIAKI_ENABLE_STEAM_SHORTCUT=OFF", "-DCHIAKI_ENABLE_STEAMDECK_NATIVE=OFF",
                    "-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384",
                )
            }
        }
    }

    ndkVersion = "28.2.13676358"
    if (nativniZ == null) externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("../third_party/chiaki-ng/CMakeLists.txt")
        }
    } else {
        sourceSets["main"].jniLibs.srcDir(nativniZ)
    }

    // Stálý podpis: všechny verze musí být podepsané stejným klíčem, jinak je televize nenainstaluje
    // přes starší verzi a uživatel přijde o spárování i nastavení. Klíč v repozitáři není a nesmí
    // být: sestavení na GitHubu ho rozbalí z tajného údaje RECAPTION_KEY_BASE64 do dočasného
    // souboru a jeho cestu předá v RECAPTION_KEY_FILE; heslo je v RECAPTION_KEY_PASSWORD.
    // Bez nich (např. sestavení mimo GitHub) se podepíše dočasným klíčem – takové APK nejde
    // nainstalovat přes vydanou verzi a sestavení ho nevydá.
    val signingPassword: String? = System.getenv("RECAPTION_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    val signingFile: File? = System.getenv("RECAPTION_KEY_FILE")?.takeIf { it.isNotBlank() }
        ?.let { File(it) }?.takeIf { it.isFile }
    val stableSigning = signingPassword != null && signingFile != null
    signingConfigs {
        if (stableSigning) create("stable") {
            storeFile = signingFile
            storeType = "pkcs12"
            storePassword = signingPassword
            keyAlias = "recaption"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (stableSigning) "stable" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // nativní knihovny jsou v APK komprimované (menší soubor ke stažení)
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // s -PabiApk neplatí abiFilters – knihovny pro x86 (z ML Kit, WebRTC) by šly i do celého APK (75 MB místo 41)
            if (project.hasProperty("abiApk")) excludes += listOf("lib/x86/**", "lib/x86_64/**")
        }
    }

    // APK zvlášť pro každý procesor (od 1.00-rc23, přání majitele: rychlejší stahování a instalace aktualizace –
    // zhruba poloviční soubor). Jen s -PabiApk (build.yml, vydání): vznikne app-arm64-v8a-release.apk,
    // app-armeabi-v7a-release.apk a app-universal-release.apk (= ReCaption.apk pro web, Downloader a USB).
    // Aktualizace v aplikaci si bere soubor podle procesoru (Updater.apkFile). Bez -PabiApk jedno APK jako dřív.
    splits {
        abi {
            isEnable = project.hasProperty("abiApk")
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Naše úprava nativní části (bez zvuku a bez nativního dekodéru videa) se před sestavením
// zkopíruje na místo původní android/app v knihovně chiaki-ng.
val patchChiaki by tasks.registering(Copy::class) {
    from("src/main/cpp")
    into("../third_party/chiaki-ng/android/app")
}
tasks.named("preBuild") { dependsOn(patchChiaki) }

// Aplikace pro televizi LG (webos/obchod/app) jako balíček .ipk přímo v APK: z telefonu nebo boxu
// jde nainstalovat do televize ve vývojářském režimu (LgInstaller.kt). Balíček skládá
// core/LgIpk.kt (přes buildSrc) stejně jako ares-package, jen s pevným datem 1. 1. 2000.
// Po sestavení se přečte zpět a porovná se zdroji. Výsledek: assets lg/receiver.ipk
// a lg/receiver-manifest.json (id, verze, velikost, sha256). Nic se necommituje.
val lgAssets = layout.buildDirectory.dir("generated/lgAssets")
val lgBalicek by tasks.registering {
    val src = rootProject.file("webos/obchod/app")
    inputs.dir(src)
    outputs.dir(lgAssets)
    doLast {
        val files = src.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
            .associate { it.relativeTo(src).invariantSeparatorsPath to it.readBytes() }
        val ipk = cz.recaption.core.LgIpk.build(files)
        val (id, version) = cz.recaption.core.LgIpk.verify(ipk, files)
        require(id == cz.recaption.core.LgIpk.APP_ID) { "webos/obchod/app má jiné id: $id" }
        val out = lgAssets.get().dir("lg").asFile
        out.deleteRecursively(); out.mkdirs()
        out.resolve("receiver.ipk").writeBytes(ipk)
        out.resolve("receiver-manifest.json").writeText(cz.recaption.core.LgIpk.manifest(id, version, ipk))
        logger.lifecycle("LG: ${cz.recaption.core.LgIpk.fileName(id, version)} (${ipk.size} B)")
    }
}
android.sourceSets.getByName("main").assets.srcDir(lgAssets)
tasks.named("preBuild") { dependsOn(lgBalicek) }

// Aplikace pro televize Samsung (tizen/app) jako NEPODEPSANÝ balíček .wgt v APK. Podepsat ho jde
// jen certifikátem Samsungu pro konkrétní televizi, proto ho průvodce (SamsungInstallActivity)
// jen uloží a podpis s nahráním nechá nástroji Samsungu. Skládá core/SamsungWgt.kt (přes buildSrc).
// Hlídá i to, že se společné soubory (app.js, style.css, index.html) ve webos/obchod/app
// a tizen/app nerozešly a že verze v platforma.js sedí s appinfo.json / config.xml.
// Výsledek: assets samsung/receiver.wgt a samsung/receiver-manifest.json. Nic se necommituje.
val samsungAssets = layout.buildDirectory.dir("generated/samsungAssets")
val samsungBalicek by tasks.registering {
    val src = rootProject.file("tizen/app")
    val lg = rootProject.file("webos/obchod/app")
    inputs.dir(src)
    inputs.dir(lg)
    outputs.dir(samsungAssets)
    doLast {
        fun read(dir: File) = dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
            .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes() }
        val files = read(src)
        val lgFiles = read(lg)
        val diff = cz.recaption.core.SamsungWgt.sharedDiff(lgFiles, files)
        require(diff.isEmpty()) {
            "tizen/app se rozešel s webos/obchod/app: $diff – uprav oba stejně (zkopíruj soubor z webos/obchod/app do tizen/app)"
        }
        val (id, version) = cz.recaption.core.SamsungWgt.config(String(files.getValue("config.xml")))
        require(id == cz.recaption.core.SamsungWgt.APP_ID) { "tizen/app/config.xml má jiné id: $id" }
        require(cz.recaption.core.SamsungWgt.platformVersion(String(files.getValue("platforma.js"))) == version) {
            "tizen/app: verze v platforma.js nesedí s config.xml ($version)"
        }
        val lgVersion = cz.recaption.core.LgIpk.appInfo(String(lgFiles.getValue("appinfo.json"))).second
        require(cz.recaption.core.SamsungWgt.platformVersion(String(lgFiles.getValue("platforma.js"))) == lgVersion) {
            "webos/obchod/app: verze v platforma.js nesedí s appinfo.json ($lgVersion)"
        }
        val wgt = cz.recaption.core.SamsungWgt.build(files)
        cz.recaption.core.SamsungWgt.verify(wgt, files)
        val out = samsungAssets.get().dir("samsung").asFile
        out.deleteRecursively(); out.mkdirs()
        out.resolve("receiver.wgt").writeBytes(wgt)
        out.resolve("receiver-manifest.json").writeText(cz.recaption.core.SamsungWgt.manifest(id, version, wgt))
        logger.lifecycle("Samsung: ${cz.recaption.core.SamsungWgt.fileName(version)} (${wgt.size} B)")
    }
}
android.sourceSets.getByName("main").assets.srcDir(samsungAssets)
tasks.named("preBuild") { dependsOn(samsungBalicek) }

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // QR kód s přihlašovací adresou Sony (přihlášení účtem PlayStation přes telefon)
    implementation("com.google.zxing:core:3.5.3")

    // Instalace aplikace do televize LG ve vývojářském režimu (SSH/SFTP, udržovaná větev JSch)
    implementation("com.github.mwiede:jsch:2.28.8")

    // Nahrání do Android TV z telefonu přes ladění po síti (ADB, port 5555) – mobile.dev dadb,
    // čisté Kotlin, Apache 2.0, s okio asi 0,4 MB (navrhy/instalace-android-tv.md, AtvInstaller.kt)
    implementation("dev.mobile:dadb:2.0.0")

    // Xbox: příjem obrazu z konzole přes vzdálené hraní (WebRTC; Google libwebrtc v sestavení
    // od Stream, BSD/Apache 2.0). Do APK jdou jen procesory ARM (abiFilters výše).
    implementation("io.getstream:stream-webrtc-android:1.3.7")

    testImplementation("junit:junit:4.13.2")
}
