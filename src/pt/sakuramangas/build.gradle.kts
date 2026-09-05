import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sakura Mangás"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Sakura Mangás"
        baseUrl = "https://sakuramangas.org"
        lang = "pt-BR"
    }

    deeplink {
        path("/obras/..*")
    }
}

dependencies {
    compileOnly("androidx.webkit:webkit:1.15.0")
}
