description = ""

val coroutines_version: String by extra

kotlin.sourceSets {
    val jvmAndNixMain by getting {
        dependencies {
            api(kotlin("test"))

            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            implementation(project(":ktor-server:ktor-server-test-host"))
        }
    }

    val jvmMain by getting {
        dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-partial-content"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))

            implementation(kotlin("test-junit"))

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
