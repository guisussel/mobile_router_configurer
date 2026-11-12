# Roteador

App iOS e Android para configurar e acompanhar o roteador de casa pelo Wi-Fi: status da
internet e da fibra, tráfego ao vivo, aparelhos conectados, nome/senha/canal/potência do Wi-Fi,
força do sinal no ponto onde você está e reinício. Fala direto com a interface web do roteador
na rede local, sem servidor e sem conta.

Kotlin Multiplatform + Compose Multiplatform. Suporte completo ao **Askey RTF8225VW** (Vivo Fibra);
qualquer outro roteador abre no **modo básico** (driver genérico), e modelos novos entram como drivers.

| | Askey RTF (Vivo) | Outros roteadores (modo básico) |
| --- | --- | --- |
| Internet no ar | sim | sim (teste de conexão; IP e tempo ligado se tiver UPnP) |
| Sinal da fibra | sim | não |
| Tráfego ao vivo | sim | só com UPnP ligado no roteador |
| Aparelhos conectados | sim, com MAC e banda | sim, por varredura da rede (só IP) |
| IP fixo e nome por aparelho | sim | não |
| Bloquear internet de um aparelho | sim (regra de firewall) | não |
| Wi-Fi: nome, senha, canal, WPS… | sim | não (botão para a página do roteador) |
| Reiniciar | sim | não |
| Sinal do celular | sim | sim |

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
| `drivers:generic` | Modo básico para qualquer roteador: UPnP IGD (se ligado), teste de conexão e varredura TCP da rede /24. |
| `feature:*` | As cinco abas e o login. |
| `composeApp` | `App()`, navegação, abas, montagem do Koin, entrada do iOS. |
| `build-logic` | Convention plugins: cada `build.gradle.kts` de módulo tem 3 linhas. |

### Como estender

- **Outro roteador**: crie `drivers/<nome>` com uma `RouterDriverFactory` (reconhece o modelo
  pelas páginas públicas) e um `RouterDriver` que declara suas `capabilities`; inclua o módulo
  Koin em `driverModules` (`composeApp/.../di/AppModules.kt`). As telas se adaptam ao que o driver
  declara. Referências prontas para adaptar: `tplinkrouterc6u` (TP-Link/Mercusys), `sagemcom_api`,
  `fritzconnection` (TR-064), `asusrouter`, ubus do OpenWrt.
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
- IP fixo: `sv_setvar.cmd` com `varName=modDhcpStatic` e `varValue=iid/nome/MAC/ip` (`iid` -1 = nova);
  remover com `delDhcpStatic`. O IP precisa estar na faixa do DHCP (`dhcpStart`..`dhcpEnd`).
- Bloqueio de internet: regra "Rejeita Local" em `sv_firewall.cmd?action=create`, origem = IP do
  aparelho, todos os protocolos (176), com nome `Bloq-<ip>` para o app reconhecer as suas.
- O filtro de MAC do Wi-Fi é uma lista de **permitidos**: não serve para expulsar um aparelho só.
- WPS só pareia pelo botão físico; pelo app dá para ligar e desligar.
- Os contadores de tráfego só atualizam a cada **~5–6 s** no roteador; a tela atualiza a cada 1 s,
  mas o valor é calculado entre duas atualizações reais.
- A página de estatísticas leva **~560 ms** para responder (mediana de 30 leituras seguidas).

## Limitações conhecidas

- **iOS não foi compilado**: o código iOS (Keychain, entrada SwiftUI, `project.yml`) foi escrito
  sem um Mac. A primeira compilação no Xcode pode pedir ajustes pequenos.
- O iOS não libera a força do sinal para apps comuns; a aba Sinal fica limitada no iPhone.
- No Android, o nome da rede (SSID) só aparece com permissão de localização, que o app ainda não pede.
- **Alterar Wi-Fi** funcionou no roteador real (troca de canal do 2,4 GHz pelo app no tablet).
- **IP fixo e bloqueio de internet** foram testados só contra o roteador falso: a leitura funciona no
  aparelho real, mas a gravação ainda não foi executada nele. Em especial, falta confirmar que o
  roteador aplica a regra de bloqueio antes da regra padrão "aceitar LAN → WAN".
- Modo básico: a varredura acha aparelhos que respondem em portas comuns (a maioria); MAC e nomes
  não aparecem (Android 10+ e iOS escondem MACs; nomes dependem do DNS da rede).
- Versão de uso pessoal: release assinado com a chave de debug, sem minificação. Antes de publicar,
  configurar assinatura, ícones finais, política de privacidade e textos das lojas.
