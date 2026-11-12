# Notas técnicas: Askey RTF (Vivo Fibra)

O que foi levantado da interface web do Askey **RTF8225VW-SV**, firmware
`BR_SV_g2.5_RTF_TEF004_V3.9`, para escrever o driver `drivers/askey`. Serve de referência
para manter o driver e para quem for escrever drivers de outros modelos.

## Como a interface funciona

- Não há API. O estado vem em variáveis JavaScript dentro de cada página `.asp`
  (`var opticalPower = '…'`), que o app lê com `PageScript`.
- **Sessão:** abrir `login.asp` cria o cookie `_httpdSessionId_`. O login é um POST em
  `cgi-bin/te_acceso_router.cgi` com `loginUsername` e `loginPassword`, cada caractere
  com XOR `0x1f`. Sessão expirada = redirecionamento para `login.asp`.
- **Bloqueio por tentativas:** depois de várias senhas erradas, `login.asp` traz
  `bruteTime` > 0 (segundos até liberar).
- **Token por página:** cada página traz um `sessionKey` que a próxima alteração precisa devolver.
- **Requisições aos CGIs:** respondem **400** se o POST não tiver `Content-Type` de
  formulário, mesmo com corpo vazio. Os parâmetros vão na query string, e o corpo vai vazio.

## Endpoints usados

| Uso | Endpoint | Observação |
| --- | --- | --- |
| Identificar o modelo | `GET about-power-box.asp` | Público, sem login |
| Status, fibra, aparelhos | `GET index_cliente.asp` | `opticalPower`, `pppUptime`, `lanHostList`… |
| Tráfego e clientes Wi-Fi | `GET device-management-statistics.asp` | `wlanIntfSts`, `ethIntfSts`, `wlan*AssociatedList` |
| Ler Wi-Fi | `GET settings-wireless-network-u.asp` | Um bloco de variáveis por rádio |
| Canais válidos | `POST cgi-bin/sv_getvar.cmd?varName=channel_list&wifi_id=…&standard=…&bandwidth=…` | Lista separada por vírgulas |
| Gravar Wi-Fi 2,4 GHz | `cgi-bin/te_wifi.wl` | Primeiro POST com `validation_only=1` (precisa responder `0`), depois GET com os mesmos parâmetros |
| Gravar Wi-Fi 5 GHz | `cgi-bin/te_wifi_5ghz.wl` | Igual ao 2,4 GHz |
| Reservas de IP | `POST cgi-bin/sv_getvar.cmd?varName=dhcpStatic` | `iid/nome/mac/ip\|…` |
| Criar ou alterar reserva | `POST cgi-bin/sv_setvar.cmd?varName=modDhcpStatic&varValue=iid/nome/MAC/ip` | `iid` -1 = nova; IP dentro de `dhcpStart`..`dhcpEnd` |
| Remover reserva | `POST cgi-bin/sv_setvar.cmd?varName=delDhcpStatic&varValue=iid` | |
| Regras de firewall | `POST cgi-bin/sv_firewall.cmd?action=show\|create\|delete&inst=…` | Ids em `firewallRuleIndexList` |
| Reiniciar | `POST cgi-bin/cbReboot.xml?sessionKey=…` | Token tirado de `popup-reboot.asp` |

## Particularidades

- **Nome e senha do Wi-Fi:** a interface só edita os da rede principal (2,4 GHz). A rede
  5 GHz acompanha, com o sufixo `-5GHz`.
- **Filtro de MAC:** funciona como lista de **permitidos**. Não serve para expulsar um
  aparelho só, porque derrubaria todos os outros.
- **WPS:** pareia só pelo botão físico. Pela interface dá apenas para ligar e desligar.
- **Bloqueio de internet:** o app cria uma regra "Rejeita Local" (LAN → WAN, todos os
  protocolos = `176`, origem = IP do aparelho) com nome `Bloq-<ip>`, para reconhecer as
  regras que ele mesmo criou. A regra padrão do roteador ("Accept traffic from LAN to HSI
  WAN") nunca é alterada.
- **Contadores de tráfego:** só atualizam a cada ~5–6 s. O app calcula a taxa entre duas
  atualizações reais e mantém o último valor entre elas.
- **Desempenho:** a página de estatísticas leva ~560 ms para responder (mediana de 30
  leituras seguidas). O polling de 1 s cabe, com pouca folga.
- **Sinal da fibra:** a faixa aceitável do receptor GPON vai de -8 a -27 dBm. Abaixo de
  -24 dBm sobra pouca margem.

## Situação dos testes no aparelho real

| Função | Situação |
| --- | --- |
| Leitura (status, aparelhos, Wi-Fi, tráfego, reservas) | Verificada |
| Alterar Wi-Fi (canal) | Verificada |
| Reiniciar | Verificada |
| Criar e remover reserva de IP | Testada só contra o roteador simulado |
| Bloquear e liberar internet | Testada só contra o roteador simulado. Falta confirmar que a regra vale antes da regra padrão "aceitar LAN → WAN" |
