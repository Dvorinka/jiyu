plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    // Od Kotlinu 2.0 je Compose compiler soucasti Kotlinu a konfiguruje se pluginem,
    // ne pres composeOptions.kotlinCompilerExtensionVersion (to uz AGP ignoruje).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    // Firebase (zdarma) — pluginy se aplikují jen v app/build.gradle.kts,
    // a to jen pokud existuje app/google-services.json (viz komentář tam)
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}
