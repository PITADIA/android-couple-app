// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.3.15" apply false
}

buildscript {
    dependencies {
        // Import the Firebase BoM
        // When using the BoM, don't specify versions in Firebase dependencies
    }
}