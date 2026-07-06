plugins {
    alias(libs.plugins.spring.boot)
}

description = "Worker bootstrap: consumes the video-processing queue and runs FFmpeg."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Web + actuator serve the health/metrics endpoints (the worker had no health check at all).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(platform(libs.awssdk.bom))
    implementation(libs.awssdk.s3)
    runtimeOnly("org.postgresql:postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("streamtube-worker.jar")
}
