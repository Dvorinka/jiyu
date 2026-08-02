pluginManagement {
    // Pozn.: do AGP 8.5.2 tu byl pin na R8 8.7.18. R8 8.5.35, který si AGP tahal sám, měl
    // bug, kvůli kterému KAŽDÝ release build spadl na
    // "R8: java.util.ConcurrentModificationException" v com.android.tools.r8.shaking -
    // nebyla to chyba v našem kódu ani v proguard-rules.pro. Kvůli tomu se dlouho releasovalo
    // DEBUG APK, což mělo tichý a mnohem horší následek: Crashlytics se zapíná jen pro
    // !BuildConfig.DEBUG (viz JiyuApp.initFirebase), takže ve VŠECH vydaných buildech bylo
    // hlášení pádů vypnuté a žádný crash se nikdy nenahlásil.
    //
    // AGP 8.13 si nese vlastní, mnohem novější R8 - ponechaný pin by ho naopak DEGRADOVAL,
    // proto je pryč. Kdyby se release build zase rozbil na R8, je tohle první místo, kam se
    // podívat; `assembleRelease` to musí ověřit při každém vydání.
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Jiyu"
include(":app")
