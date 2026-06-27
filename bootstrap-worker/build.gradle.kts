plugins {
    alias(libs.plugins.spring.boot)
}

description = "Worker bootstrap: Spring Boot app for video processing (minimal in Phase 01)."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("streamtube-worker.jar")
}
