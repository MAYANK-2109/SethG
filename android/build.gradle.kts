// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Redirect build output outside OneDrive to prevent file-lock conflicts caused by OneDrive syncing
// build intermediates while Gradle is actively writing/deleting them.
// Only on Windows checkouts inside OneDrive — everyone else keeps the normal build/ folder.
if (System.getProperty("os.name").startsWith("Windows") && rootDir.path.contains("OneDrive")) {
    allprojects {
        layout.buildDirectory.set(File("C:/gradle-builds/SethG/${project.name}"))
    }
}
