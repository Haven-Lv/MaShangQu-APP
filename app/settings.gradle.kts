pluginManagement {
    repositories {
        // 国内镜像优先：Maven Central CDN 按 TLS 指纹拦截 Java 客户端（403），阿里云镜像不受影响
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "IdCodeAgg"

include(":app")
include(":core:model")
include(":core:network")
include(":core:storage")
include(":core:webview")
include(":platform:api")
include(":platform:pdd")
include(":platform:taobao")
include(":platform:jd")
include(":platform:sf")
include(":platform:ryll")
