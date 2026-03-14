plugins {
    java
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paper_api_version: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paper_api_version")
    implementation(project(":common"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from(project(":common").sourceSets.main.get().output)
}
