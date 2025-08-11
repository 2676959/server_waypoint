plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains:annotations:26.0.2")
    api("org.slf4j:slf4j-api:1.7.30")
    api("com.google.code.gson:gson:2.13.1")
    api("io.netty:netty-buffer:4.1.+")
}