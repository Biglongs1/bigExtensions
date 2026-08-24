# Extensions

Extensões de mangá para [Mihon](https://mihon.app) e forks.

## Adicionar no app

Mihon 0.20.1+ / Aniyomi: adicione a URL do repositório em **Mais → Configurações → Navegar → Repositórios de extensões**:

```
https://raw.githubusercontent.com/Biglongs1/extensions/repo/index.min.json
```

## Fontes

| Fonte | Idioma |
| --- | --- |
| KuroMangas | pt-BR |
| MangaLivre.org | pt-BR |
| NoxManga | pt-BR |
| Yomu Comics | pt-BR |

## Desenvolvimento

Requer JDK 17 e o Android SDK.

```sh
./gradlew :src:pt:<fonte>:assembleDebug
```

O APK sai em `src/pt/<fonte>/build/outputs/apk/debug/`.

## Publicação

O workflow `build_push.yml` compila as extensões alteradas a cada push na `main`, assina, publica os APKs numa release e atualiza o índice na branch `repo`.

Secrets necessários:

| Secret | Descrição |
| --- | --- |
| `SIGNING_KEY` | keystore `.jks` em base64 |
| `ALIAS` | alias da chave |
| `KEY_STORE_PASSWORD` | senha da keystore |
| `KEY_PASSWORD` | senha da chave |
| `SIGNING_KEY_FINGERPRINT` | SHA-256 do certificado, sem `:` e em minúsculas |

Gerar a keystore e obter o fingerprint:

```sh
keytool -genkey -v -keystore signingkey.jks -keyalg RSA -keysize 2048 -validity 10000 -alias key
keytool -list -v -keystore signingkey.jks -alias key | grep SHA256
base64 -w 0 signingkey.jks
```

## Créditos

A infraestrutura de build vem do [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), sob Apache 2.0.
