plugins {
    kotlin("jvm")
}

dependencies {
    api(libs.gson)
}

kotlin {
    jvmToolchain(17)
}
