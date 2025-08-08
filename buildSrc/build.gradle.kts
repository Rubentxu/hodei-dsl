plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Kotlin and serialization plugins
    implementation(libs.kotlinGradlePlugin)
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
}
