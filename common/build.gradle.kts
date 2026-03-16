plugins {
    java
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.2")
}

tasks.test {
    useJUnitPlatform()
}
