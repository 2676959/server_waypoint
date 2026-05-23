plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.15.5" apply false
    id("net.minecraftforge.gradle") version "[7.0.11,8.0)" apply false
    id("net.minecraftforge.renamer") version "1.1.0" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("net.neoforged.gradle.userdev") version "7.1.27" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
