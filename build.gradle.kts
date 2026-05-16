// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            // Cache dynamic versions for 24 hours
            cacheDynamicVersionsFor(24, "hours")
            // Cache changing modules (snapshots) for 24 hours
            cacheChangingModulesFor(24, "hours")
        }
    }
}
