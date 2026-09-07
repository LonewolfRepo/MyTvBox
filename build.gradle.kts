plugins {
    id("com.android.application") version "8.5.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false // Updated from 1.9.22
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false // Updated KSP to match Kotlin 1.9.24
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}