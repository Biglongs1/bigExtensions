import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Atsumaru"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://atsu.moe"
        lang = "en"
    }

    deeplink {
        path("/manga/..*")
        path("/read/..*")
    }
}
