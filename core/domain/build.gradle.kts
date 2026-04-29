plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
            }
        }

        androidMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
            }
        }
    }
}
