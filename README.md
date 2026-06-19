# NGB AutoRoad

**App Android de gestão inteligente para motoristas de aplicativo** (Uber, 99, inDrive, Cabify)

## Funcionalidades Principais

- **Overlay flutuante** com análise em tempo real de corridas (score, R$/km, R$/hora, distância, bairros)
- **35 modelos de card** personalizáveis em 7 categorias
- **Score multi-critério** com cores dinâmicas (verde/amarelo/laranja/vermelho)
- **Controle financeiro** completo (turnos, despesas operacionais, despesas fixas, projeção)
- **Simulador admin** para testar cards e corridas
- **Acessibilidade** (A+/A- para ajustar tamanho da fonte)
- **Multi-idioma** (PT-BR, ES)
- **Modo economia de bateria** (Normal/ECO/Ultra-ECO)
- **Ranking de bairros** por R$/km, R$/hora ou total
- **Relatório PDF** e **exportação CSV**
- **Aprendizado local** (padrões estatísticos sem nuvem)

## Requisitos

- Android 8.0+ (API 26)
- Permissão de Acessibilidade
- Permissão de Overlay (desenhar sobre outros apps)

## Build

```bash
# JDK 17 necessário
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH

# Compilar APK release (assinado)
./gradlew assembleRelease

# APK gerado em:
# app/build/outputs/apk/release/app-release.apk
```

## Estrutura do Projeto

```
ngb-autoroad-privacy/
├── app/
│   ├── build.gradle.kts          # Config do módulo app
│   ├── proguard-rules.pro        # Regras ProGuard/R8
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ngbautoroad/
│       │   ├── NGBAutoRoadApp.kt
│       │   ├── data/
│       │   │   ├── db/           # Room Database
│       │   │   ├── model/        # Modelos de dados
│       │   │   └── prefs/        # DataStore preferences
│       │   ├── domain/           # Lógica de negócio
│       │   ├── service/          # Services (Overlay, Accessibility, OCR)
│       │   ├── ui/               # Activities e Tabs
│       │   └── util/             # Extensões
│       └── res/
│           ├── values/           # Strings PT-BR, temas
│           ├── values-es/        # Strings ES
│           ├── drawable/         # Ícones
│           ├── mipmap-*/         # Launcher icons
│           └── xml/              # Accessibility config
├── keystore/                     # Keystore de release
├── scripts/                      # Scripts de automação
├── .github/workflows/            # CI/CD (build + release)
├── build.gradle.kts              # Config raiz
├── settings.gradle.kts
├── gradle.properties
├── MEMORIA_PROJETO.md            # Histórico completo do projeto
└── CHANGELOG.md                  # Changelog por versão
```

## Versão Atual

**v5.3.2** (versionCode 33) — 19/06/2026

## Licença

Projeto privado — todos os direitos reservados.
