// Pomocný kód pro sestavení: core/LgIpk.kt a core/SamsungWgt.kt z aplikace – balíčky pro televize LG
// a Samsung (assets/lg/receiver.ipk, assets/samsung/receiver.wgt) tak skládá tentýž kód, který pokrývají unit testy.
// Jen standardní knihovna Javy, žádný Node ani Python – funguje na GitHubu i v sestavit-na-pc.ps1.
plugins { `embedded-kotlin` }
repositories { mavenCentral() }
sourceSets["main"].kotlin {
    srcDir("../app/src/main/java")
    include("cz/recaption/core/LgIpk.kt", "cz/recaption/core/SamsungWgt.kt")
}
