pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        exclusiveContent {
            forRepository {
                maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
            }
            filter {
                includeModule("org.reactivestreams", "reactive-streams")
                includeGroup("com.squareup.okhttp3")
                includeGroup("com.squareup.okio")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "TarkastusCloud"
include(":app", ":tarkastus")
