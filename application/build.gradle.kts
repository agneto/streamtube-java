// Application layer — use cases / interactors orchestrating domain ports.
// Uses Spring stereotypes (@Service/@Transactional) for wiring; stays free of
// web and persistence concerns. The domain module remains framework-free.
description = "Application layer: use cases over domain ports."

dependencies {
    implementation(project(":domain"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
}
