# Roteador

App Android e iOS para acompanhar e configurar o roteador de casa pelo celular. A página de
administração do roteador vira uma tela de celular: status da internet, sinal da fibra,
tráfego ao vivo, aparelhos conectados, Wi-Fi e reinício.

O app conversa direto com o roteador pela rede local. Não usa servidor, não pede conta e não
manda nenhum dado para fora de casa.

Feito em Kotlin Multiplatform e Compose Multiplatform, com o mesmo código nas duas plataformas.

> **Status:** projeto pessoal em desenvolvimento. O Android funciona e é testado com um roteador
> real. O iOS está estruturado, mas ainda não foi compilado (veja [Limitações](#limitações)).

## O que o app faz

| Aba | O que mostra e o que permite fazer |
| --- | --- |
| **Início** | Saúde da conexão em uma frase, sinal da fibra numa barra com a faixa ideal, internet (IP público, tempo ligado, DNS), portas com cabo e gráfico de download e upload do último minuto. |
| **Aparelhos** | Quem está conectado, separado por Wi-Fi 5 GHz, Wi-Fi 2,4 GHz e cabo, com IP, MAC e tempo conectado. Tocar e segurar um aparelho abre um menu para copiar IP ou MAC, reservar um **IP fixo com nome** e **bloquear ou liberar a internet** dele. |
| **Wi-Fi** | Nome da rede, senha, segurança (WPA2, WPA2/WPA3, WPA3), canal, largura do canal, potência, **WPS**, rede oculta e ligar ou desligar cada banda. Antes de aplicar, o app mostra um resumo do que muda e avisa que o Wi-Fi vai reiniciar. |
| **Sinal** | Força do sinal no ponto onde o celular está, atualizada a cada segundo, num gráfico dos últimos 2 minutos com as faixas ótimo e fraco marcadas. Serve para andar pela casa e achar os pontos ruins. |
| **Ajustes** | Modelo e firmware do roteador, reinício com acompanhamento até a internet voltar, atalho para a página do próprio roteador e saída (com opção de esquecer a senha). |

### Roteadores compatíveis

| | Askey RTF (Vivo Fibra) | Qualquer outro roteador (modo básico) |
| --- | --- | --- |
| Internet no ar | sim | sim |
| IP público e tempo ligado | sim | se o roteador tiver UPnP ligado |
| Sinal da fibra | sim | não |
| Tráfego ao vivo | sim | se o roteador tiver UPnP ligado |
| Aparelhos conectados | sim, com MAC e banda | sim, só o IP (por varredura da rede) |
| IP fixo e bloqueio de internet | sim | não |
| Configurar Wi-Fi | sim | não (o app abre a página do roteador) |
| Reiniciar | sim | não |
| Sinal do celular | sim | sim |

O suporte completo foi feito e testado com o **Askey RTF8225VW** que a Vivo entrega na fibra.
Outros modelos entram como novos drivers (veja [Como estender](#como-estender)).

## Como funciona

1. **Descobrir o roteador.** O app sugere o endereço do roteador a partir do Wi-Fi em que o
   celular está e lê uma página pública dele para saber o modelo antes de pedir a senha.
2. **Escolher o driver.** Cada família de roteador tem um *driver*, que sabe falar com a
   interface web daquele modelo. Se nenhum driver reconhece o aparelho, entra o **driver
   genérico**, que funciona com qualquer roteador:
   - lê o UPnP do roteador quando ele está ligado;
   - testa se a internet responde;
   - varre a rede local para listar os aparelhos.
3. **Falar como o navegador.** Roteadores domésticos raramente têm API. O driver faz o que a
   página de administração faz: faz login, lê as variáveis que a página traz e envia os mesmos
   formulários. Antes de gravar uma mudança, ele pede ao roteador para validá-la, quando o
   roteador oferece isso.
4. **Uma sessão, uma fila.** Todas as chamadas passam por um único repositório, uma de cada vez,
   porque o roteador aceita uma sessão só. Se a sessão cai, o app entra de novo sozinho e repete
   a chamada.
5. **Tempo real.** O tráfego e o sinal são lidos a cada segundo, e o status a cada 5 segundos.
   As leituras só acontecem com a tela aberta. Como o roteador atualiza os contadores no próprio
   ritmo, a taxa é calculada entre duas leituras que de fato mudaram.

**Privacidade e segurança**

- **Senha:** fica no armazenamento seguro do sistema (Android Keystore e iOS Keychain), só se
  você pedir para lembrar.
- **Alcance da rede:** o app só fala com o roteador e com os aparelhos da rede local.
  - O teste de "internet no ar" do modo básico só abre uma conexão com servidores DNS públicos,
    sem enviar dados.
  - Os roteadores domésticos só oferecem HTTP. Por isso o tráfego sem criptografia é permitido,
    e somente para a rede local.
- **Ações que derrubam algo:** pedem confirmação e explicam o efeito antes. Isso vale para
  aplicar o Wi-Fi, reiniciar e bloquear um aparelho.

## Arquitetura

O projeto é dividido em módulos pequenos com responsabilidades claras. A ideia é que adicionar
um roteador ou uma tela nova não mexa no resto.

```
androidApp ─┐
iosApp ─────┴─► composeApp ─────► feature:*  (login, dashboard, devices, wifi, signal, settings)
               navegação, abas,       │
               injeção (Koin)         ▼
                    │             core:data ──────► core:platform
                    │             repositório:      sinal do celular,
                    │             sessão, fila,     armazenamento seguro
                    │             polling, tráfego
                    │                 │
                    ├─► drivers:askey ┤
                    └─► drivers:generic
                                      ▼
                                  core:router ────► core:model
                                  contrato do       tipos do domínio
                                  driver, HTTP
```

| Camada | Papel |
| --- | --- |
| `core:model` | Tipos do domínio, em Kotlin puro: status, aparelhos, configuração do Wi-Fi, capacidades e erros. |
| `core:router` | Contrato `RouterDriver`, registro que escolhe o driver pelo modelo, cliente HTTP e leitor das páginas dos roteadores. |
| `core:platform` | O que depende do sistema: sinal do Wi-Fi do celular e armazenamento seguro. |
| `core:data` | Repositório único: sessão, fila de chamadas, novo login automático, polling e cálculo de tráfego. |
| `core:designsystem` | Tema claro e escuro, cores de status, ícones, cartões e gráficos. |
| `drivers:*` | Um módulo por família de roteador. Cada driver declara suas **capacidades**, e as telas se adaptam a elas. |
| `feature:*` | Uma tela por módulo (tela, ViewModel e módulo Koin). As features não dependem umas das outras. |
| `composeApp` | Junta tudo: navegação, abas e injeção de dependências. É também a entrada do iOS. |
| `build-logic` | Convention plugins do Gradle. O `build.gradle.kts` de cada módulo tem poucas linhas. |

**Bibliotecas:** Compose Multiplatform, Ktor (HTTP e sockets), Koin (injeção de dependências),
kotlinx.coroutines, Navigation Compose e Kermit (logs). **Testes:** kotlin.test, Turbine e um
roteador falso feito com o `MockEngine` do Ktor.

### Como estender

- **Novo roteador:**
  1. Crie `drivers/<nome>` com uma `RouterDriverFactory`, que reconhece o modelo, e um
     `RouterDriver`, que declara o que suporta.
  2. Registre o driver em `driverModules`.

  As telas não mudam. As notas do driver Askey em [`docs/askey-rtf.md`](docs/askey-rtf.md)
  servem de guia.
- **Nova tela:** crie `feature/<nome>` com o plugin `roteador.feature` e adicione a aba em
  `HomeScreen.kt`.

## Rodando

**Pré-requisitos:** JDK 17 ou mais novo e Android SDK com a plataforma 37. Crie um
`local.properties` com `sdk.dir=<caminho do SDK>`, ou abra o projeto no Android Studio, que
cria esse arquivo.

```bash
./gradlew :androidApp:installDebug   # instala no celular ou emulador conectado
./gradlew testAndroidHostTest        # testes unitários (na JVM, sem emulador)
```

**Teste com um roteador real**, só leitura (não muda nada):

```bash
ROUTER_PASSWORD='…' ./gradlew :drivers:askey:testAndroidHostTest --tests '*LiveRouterTest*' -i
ROUTER_HOST=192.168.0.1 ./gradlew :drivers:generic:testAndroidHostTest --tests '*LiveGenericTest*' -i
```

**iOS** (precisa de um Mac com Xcode):

```bash
brew install xcodegen
cd iosApp && xcodegen && open iosApp.xcodeproj
```

## Limitações

- **iOS:** o código foi escrito, mas ainda não foi compilado num Mac. O iOS também não deixa
  apps comuns lerem a força do sinal, então a aba Sinal fica limitada no iPhone.
- **Askey: IP fixo e bloqueio de internet.** As duas funções foram testadas contra o roteador
  simulado. A leitura funciona no aparelho real, mas a gravação ainda não foi confirmada nele.
- **Expulsar um aparelho do Wi-Fi:** não existe. O filtro de MAC do Askey só funciona como lista
  de permitidos.
- **Modo básico:** a varredura acha aparelhos que respondem em portas comuns e mostra só o IP.
  O Android 10+ e o iOS escondem o MAC dos outros aparelhos.
- **Publicação:** o app ainda não está preparado para as lojas. Faltam assinatura de release,
  ícones finais e política de privacidade.

## Aviso

Projeto independente, sem relação com a Vivo, a Askey ou outros fabricantes. Use só em
roteadores que você administra. Mudanças de Wi-Fi e firewall podem deixar aparelhos sem
conexão até serem desfeitas.

## Licença

Licença dupla, à sua escolha:

- [MIT](LICENSE-MIT)
- [Apache License 2.0](LICENSE-APACHE)

Contribuições enviadas a este repositório ficam sob as mesmas duas licenças, salvo aviso
explícito em contrário.
