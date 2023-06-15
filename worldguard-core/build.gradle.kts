plugins {
    `java-library`
}

applyPlatformAndCoreConfiguration()

dependencies {
    "api"(project(":worldguard-libs:core"))
    "api"("com.sk89q.worldedit:worldedit-core:${Versions.WORLDEDIT}")
    "implementation"("org.flywaydb:flyway-core:3.0")
    "implementation"("org.yaml:snakeyaml:2.0")
    "implementation"("com.google.guava:guava:${Versions.GUAVA}")
    "implementation"("com.google.code.gson:gson:2.10.1")

    "compileOnly"("com.google.code.findbugs:jsr305:${Versions.FINDBUGS}")
    "testImplementation"("org.hamcrest:hamcrest-library:2.2")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(":worldguard-libs:build")
}