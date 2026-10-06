import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android imports, so it compiles and tests on any computer.
plugins { alias(libs.plugins.kotlin.jvm) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// The core tests are a dependency-free harness (no JUnit). Run with:
//   ./gradlew :core:coreTests        or        core/run_tests.sh (no Gradle needed)
tasks.register<JavaExec>("coreTests") {
    group = "verification"
    description = "Runs the dependency-free core test harness."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.cognisense.core.CoreTestsKt")
}
tasks.named("check") { dependsOn("coreTests") }
