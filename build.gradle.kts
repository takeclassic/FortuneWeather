plugins {
    // 프로젝트 전체에서 사용할 플러그인 버전 정의 (안드로이드/KMP/Compose 연동 필수)
    kotlin("multiplatform") version "1.9.23" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.6.1" apply false
    kotlin("plugin.serialization") version "1.9.23" apply false
    id("com.codingfeline.buildkonfig") version "0.15.1" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // KMP 빌드를 위해 때로는 JetBrains 전용 저장소가 필요할 때가 있음
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/patch") }
    }
}
