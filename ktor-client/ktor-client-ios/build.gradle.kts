
kotlin.sourceSets {
    val darwinMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-client:ktor-client-features:ktor-client-websocket"))
        }
    }
}
