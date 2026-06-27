// Infrastructure layer — adapters implementing application/domain ports
// (JPA persistence, Flyway, and from later phases: S3, RabbitMQ, mail).
description = "Infrastructure layer: persistence and external adapters."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
