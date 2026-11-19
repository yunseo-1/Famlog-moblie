pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오 SDK (카카오 로그인 AUTH-01, 카카오 지도 PLC-04)
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            content {
                includeGroup("com.kakao.sdk")
                includeGroup("com.kakao.maps.open")
            }
        }
    }
}

rootProject.name = "Famlog"
include(":app")
