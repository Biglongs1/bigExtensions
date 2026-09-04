import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Vegitoons"
    versionCode = 14
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://vegitoons.black"
        lang = "pt-BR"
    }

    deeplink {
        path("/obra/..*")
    }
}
