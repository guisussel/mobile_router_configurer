# Roteador

App iOS e Android para configurar e acompanhar o roteador de casa pelo Wi-Fi: status da
internet e da fibra, tráfego ao vivo, aparelhos conectados, nome/senha/canal/potência do Wi-Fi,
força do sinal no ponto onde você está e reinício. Fala direto com a interface web do roteador
na rede local, sem servidor e sem conta.

Kotlin Multiplatform + Compose Multiplatform. Hoje suporta o **Askey RTF8225VW** (Vivo Fibra);
outros modelos entram como novos drivers.

## Rodando

Pré-requisitos: JDK 17+ e Android SDK com a plataforma 37 (`local.properties` aponta para ele).

```bash
./gradlew :androidApp:installDebug   # instala no celular/emulador conectado
./gradlew testAndroidHostTest        # todos os testes unitários (JVM, sem emulador)
```

No Android Studio, abra a pasta e rode a configuração `androidApp`.

Teste contra o roteador de verdade (só leitura, não muda nada; mede o polling de 1 s):

```bash
ROUTER_PASSWORD='…' ./gradlew :drivers:askey:testAndroidHostTest --tests '*LiveRouterTest*' -i
```

**iOS** (precisa de um Mac com Xcode):

```bash
brew install xcodegen
cd iosApp && xcodegen && open iosApp.xcodeproj   # escolha seu Apple ID em Signing e rode
```

## Arquitetura

```
androidApp ─┐                         ┌─► feature:login
iosApp ─────┴─► composeApp ───────────┼─► feature:dashboard     cada feature = tela + ViewModel + módulo Koin;
                (navegação, abas, DI) ├─► feature:devices       só enxerga core:model, core:data e
                      │               ├─► feature:wifi          core:designsystem, nunca outra feature
                      │               ├─► feature:signal
                      │               └─► feature:settings
                      │                          │
                      ├─► drivers:askey ──┐      ▼
                      │   (um módulo por  │   core:data ──► core:platform (sinal do celular, Keystore/Keychain)
                      │    família)       │   (RouterRepository: fila única, novo login automático,
                      │                   │    polling, Mbit/s, senha salva)
                      │                   ▼      │
                      └────────────────► core:router (contrato RouterDriver, HTTP, leitor de páginas)
                                                 │
                                          core:model (tipos de domínio, Kotlin puro)
```

| Módulo | O que tem |
| --- | --- |
| `core:model` | Tipos do domínio (`RouterStatus`, `ConnectedDevice`, `WifiBandConfig`…), regras de validação e os erros (`RouterException`). |
| `core:router` | Contrato `RouterDriver`/`RouterDriverFactory`, `RouterDriverRegistry` (descobre o modelo antes do login), cliente HTTP, `PageScript` (lê as variáveis JS das páginas). |
| `core:platform` | `WifiLinkProvider` (RSSI, frequência, gateway) e `SecureStore` (Android Keystore / iOS Keychain). |
| `core:data` | `RouterRepository`: uma sessão, chamadas em fila, refaz o login se a sessão cair, guarda credenciais. `LiveTrafficMonitor` e `pollEvery`. |
| `core:designsystem` | Tema claro/escuro, cores de status, ícones, cartões, gráfico de linha, formatação pt-BR. |
| `core:testing` | `FakeRouterRepository` para os testes das features. |
| `drivers:askey` | Driver do Askey RTF: parsers puros + HTTP; testes com páginas de exemplo e um roteador falso. |
| `feature:*` | As cinco abas e o login. |
| `composeApp` | `App()`, navegação, abas, montagem do Koin, entrada do iOS. |
| `build-logic` | Convention plugins: cada `build.gradle.kts` de módulo tem 3 linhas. |

### Como estender

- **Outro roteador**: crie `drivers/<nome>` com uma `RouterDriverFactory` (reconhece o modelo
  pelas páginas públicas) e um `RouterDriver`; inclua o módulo Koin em `driverModules`
  (`composeApp/.../di/AppModules.kt`). Telas e repositório não mudam.
- **Outra aba**: crie `feature/<nome>` com o plugin `roteador.feature`, adicione ao `Tab` em
  `HomeScreen.kt` e o módulo em `featureModules`.
- **Outra informação do roteador**: adicione ao `RouterDriver`, implemente nos drivers e exponha
  pelo `RouterRepository`.

## O que se descobriu sobre o Askey RTF8225VW

(firmware `BR_SV_g2.5_RTF_TEF004_V3.9`, verificado no aparelho em 12/11/2026)

- Sem API: o estado vem em variáveis JavaScript dentro de cada página `.asp`.
- Login: abrir `login.asp` para criar o cookie `_httpdSessionId_`, depois POST em
  `cgi-bin/te_acceso_router.cgi` com usuário e senha com XOR 0x1f em cada caractere.
- Cada página traz um `sessionKey` de uso único que a próxima alteração precisa devolver.
- Os CGIs respondem **400** se o POST não tiver `Content-Type` de formulário, mesmo com corpo vazio.
- Gravar Wi-Fi: pré-validação com `validation_only=1` (precisa responder `0`) e depois GET com os
  mesmos parâmetros, como a página faz. Nome e senha só da rede principal (2,4 GHz); a 5 GHz acompanha.
- Os contadores de tráfego só atualizam a cada **~5–6 s** no roteador; a tela atualiza a cada 1 s,
  mas o valor é calculado entre duas atualizações reais.
- A página de estatísticas leva **~560 ms** para responder (mediana de 30 leituras seguidas).

## Limitações conhecidas

- **iOS não foi compilado**: o código iOS (Keychain, entrada SwiftUI, `project.yml`) foi escrito
  sem um Mac. A primeira compilação no Xcode pode pedir ajustes pequenos.
- O iOS não libera a força do sinal para apps comuns; a aba Sinal fica limitada no iPhone.
- No Android, o nome da rede (SSID) só aparece com permissão de localização, que o app ainda não pede.
- **Alterar Wi-Fi** foi testado contra o roteador falso (mesmos parâmetros que a página envia), mas
  não foi executado no roteador real para não derrubar a rede. Teste primeiro algo reversível,
  como trocar o canal do 2,4 GHz.
- Versão de uso pessoal: release assinado com a chave de debug, sem minificação. Antes de publicar,
  configurar assinatura, ícones finais, política de privacidade e textos das lojas.
