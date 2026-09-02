import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaFire"
    versionCode = 32
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://mangafire.to"
    }

    deeplink {
        path("/title/..*")
    }
}
