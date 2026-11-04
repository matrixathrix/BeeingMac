// Pure Kotlin shared by the phone and the watch: the streak engine, the
// notification rules and the rating model. No Android, so it tests on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // api: the @Serializable model types are part of this module's public surface
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
