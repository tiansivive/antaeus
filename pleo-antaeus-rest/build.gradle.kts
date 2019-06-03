plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-core"))
    implementation(project(":pleo-antaeus-models"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.1")

    implementation("io.javalin:javalin:2.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
}
