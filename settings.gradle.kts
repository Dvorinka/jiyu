pluginManagement {
    // R8 novější, než jaký si přitáhne AGP 8.5.2 samo (8.5.35). Ten má bug, kvůli kterému
    // KAŽDÝ release build spadl na "R8: java.util.ConcurrentModificationException" v
    // com.android.tools.r8.shaking - není to chyba v našem kódu ani v proguard-rules.pro.
    //
    // Kvůli tomu se dlouho releasovalo DEBUG APK, což mělo tichý a mnohem horší následek:
    // Crashlytics se zapíná jen pro !BuildConfig.DEBUG (viz JiyuApp.initFirebase), takže
    // ve VŠECH vydaných buildech bylo hlášení pádů vypnuté a žádný crash se nikdy
    // nenahlásil. Nezvedej tohle zpátky bez ověření, že `assembleRelease` projde.
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools:r8:8.7.18")
        }
    }
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
