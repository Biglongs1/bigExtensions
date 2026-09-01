@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selectedValue get() = options[state].second
}

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Todos" to "",
            "Mangá" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ),
    )

// The site's own genre taxonomy carries mojibake and near-duplicate entries, so only the
// usable slugs are listed here.
class GenreFilter :
    SelectFilter(
        "Gênero",
        listOf(
            "Todos" to "",
            "Ação" to "acao",
            "Adaptação" to "adaptacao",
            "Alta Fantasia" to "alta-fantasia",
            "Animais" to "animais",
            "Anti-Herói" to "anti-heroi",
            "Apocalipse" to "apocalipse",
            "Artes Marciais" to "artes-marciais",
            "Aventura" to "aventura",
            "Boys' Love" to "boys-love",
            "Bullying" to "bullying",
            "Comédia" to "comedia",
            "Comida" to "comida",
            "Cotidiano" to "cotidiano",
            "Crime" to "crime",
            "Crossdressing" to "crossdressing",
            "Cultivo" to "cultivo",
            "Demônio" to "demonio",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Escolar" to "escolar",
            "Esportes" to "esportes",
            "Fantasia" to "fantasia",
            "Fantasia Sombria" to "fantasia-sombria",
            "Ficção Científica" to "ficcao-cientifica",
            "Full Color" to "full-color",
            "Garotas Monstro" to "garotas-monstro",
            "Genderswap" to "genderswap",
            "Girls' Love" to "girls-love",
            "Gore" to "gore",
            "Gyaru" to "gyaru",
            "Harem" to "harem",
            "Histórico" to "historico",
            "Horror" to "horror",
            "Isekai" to "isekai",
            "Jogo" to "jogo",
            "Josei" to "josei",
            "Long Strip" to "long-strip",
            "Maduro" to "maduro",
            "Mafia" to "mafia",
            "Magia" to "magia",
            "Masmorra" to "masmorra",
            "Militar" to "militar",
            "Mistério" to "misterio",
            "Moderno" to "moderno",
            "Monstro" to "monstro",
            "Murim" to "murim",
            "Música" to "musica",
            "Oneshot" to "oneshot",
            "Overpower" to "overpower",
            "Policial" to "policial",
            "Psicológico" to "psicologico",
            "Psicopata" to "psicopata",
            "Realeza" to "realeza",
            "Realidade Virtual" to "realidade-virtual",
            "Reencarnação" to "reencarnacao",
            "Regressão" to "regressao",
            "Reverse Harem" to "reverse-harem",
            "Romance" to "romance",
            "Seinen" to "seinen",
            "Shounen" to "shounen",
            "Sistema" to "sistema",
            "Slice of Life" to "slice-of-life",
            "Sobrenatural" to "sobrenatural",
            "Sobrevivência" to "sobrevivencia",
            "Super Poderes" to "super-poderes",
            "Terror" to "terror",
            "Thriller" to "thriller",
            "Tragédia" to "tragedia",
            "Vampiros" to "vampires",
            "Vida Escolar" to "vida-escolar",
            "Vilã" to "villainess",
            "Vingança" to "vinganca",
            "Wuxia" to "wuxia",
            "Zumbi" to "zumbi",
        ),
    )
