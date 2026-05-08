plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

// Bundle runtime dependencies into buildSrc.jar so the generatePurlIndex task only needs
// a single file reference, avoiding configuration-cache issues with buildscript.configurations.
tasks.jar {
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Exclude JAR signature files — merging signed JARs into a fat JAR invalidates their signatures
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}
