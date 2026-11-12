package com.roteador.drivers.askey

/**
 * Trimmed copies of real pages (firmware BR_SV_g2.5_RTF_TEF004_V3.9) with the
 * same markup and variable formats, but made-up names, addresses and passwords.
 */
internal object AskeyFixtures {
    val about = """
        <script nonce='x'>
        	var curUserName = '';
        	var hwVersionModel='2RTF8225VW';
        </script>
        <table>
        <tr><td class="firstChild"><strong>Fabricante:</strong></td><td>Askey</td>
        <td class="firstChild"><strong>Modelo:</strong></td><td>RTF8225VW-SV</td></tr>
        <tr><td class="firstChild"><strong>Versão do Software:</strong></td><td>BR_SV_g2.5_RTF_TEF004_V3.9</td>
        <td class="firstChild"><strong>Versão do Hardware:</strong></td><td>REV4</td></tr>
        <tr><td class="firstChild"><strong>Número de Série:</strong></td><td>AB12CD34EF56</td></tr>
        </table>
    """.trimIndent()

    fun login(bruteTime: Int = 0) = """
        <script nonce='x'>
        	var gvtMode = 'service04|GPON|0';
        	var curUserName = '';
        	var bruteTime = '$bruteTime';
        </script>
        <form method="POST" action="/cgi-bin/te_acceso_router.cgi"></form>
    """.trimIndent()

    val status = """
        <script nonce='x'>
        	var curUserName = 'admin';
        	var gponUp='1';
        	var opticalPower='TX:2.730013 dBm;RX:-25.850267 dBm;LT:-40.000000 dBm;LR:-40.000000 dBm';
        	var gponStatus = 'OPERATION (O5)';
        	var pppStatus = '1';
        	var pppUptime = parseInt('1621408');
        	var pppIpv4Address = '203.0.113.10';
        	var dns4 = '198.51.100.1,198.51.100.2'.split(",");
        	var pppIpv6Address = '2001:db8:1::10/64';
        	var wlEnbl_main0 = '1';
        	var wlSsid_main0 = 'Casa';
        	var wlCurrentChannel_main0 = '11';
        	var wlEnbl_main1 = '1';
        	var wlSsid_main1 = 'Casa-5GHz';
        	var wlCurrentChannel_main1 = '52';
        	var enetStatus = '0,eth0.1,0|1,eth0.2,0|2,eth0.3,0|3,eth0.4,1'.split("|");
        	var lanHostList = [['1','','0','192.168.15.5','ETH','no','02:00:00:00:00:05','3','Static','::'],['1','Tablet','0','192.168.15.6','WIFI','no','02:00:00:00:00:06','0','DHCP','fe80::1'],['1','Notebook','0','192.168.15.10','WIFI','no','02:00:00:00:00:10','1','DHCP','fe80::2'],['0','TV-Sala','0','192.168.15.4','NONE','no','02:00:00:00:00:04','-1','DHCP','::']];
        </script>
    """.trimIndent()

    val statistics = """
        <script nonce='x'>
        	var ethIntfSts='eth1,0,0,0,0,0,0,0,0/eth2,0,0,0,0,0,0,0,0/eth3,0,0,0,0,0,0,0,0/eth4,865668,115840096,0,0,1868258,497192609,0,0';
        	var wlanIntfSts='wl0,37944161,12193082277,1147,143825,154032006,192567937680,0,0/wl1,27184254,10163342881,0,7192,74956324,79901411873,0,0';
        	var wlan2dot4GAssociatedList='02:00:00:00:00:06,15:22:37:59';
        	var wlan5GAssociatedList='02:00:00:00:00:10,00:01:02:03';
        </script>
    """.trimIndent()

    val wifi = """
        <script nonce='x'>
        	var sessionKey='1111';
        	var curWebPage='/settings-wireless-network-u.asp';
        	var ssid_id = '0';
        var acc_pt_id = '0';
        var wlAuthMode = '34';
        var wlBand = '1';
        var wlBssid = '02:00:00:00:01:01';
        var wlChannel = '0';
        var wlCurrentBw = '1';
        var wlCurrentChannel = '11';
        var wlEnbl = '1';
        var wlHide = '0';
        var wlNBwCap = '1';
        var wlNCtrlsb = '0';
        var wlPhyType = '32';
        var wlSsid = htmlDecode('Casa &amp; Cia');
        var wlTxPwrRatio = '100';
        var wlWmm = '1';
        var wlWmmUapsd = '1';
        var wlWpaPsk = htmlDecode('senha-de-teste');
        var wlWpsMode = '1';
        var wlMacFlMode = '0';
        	var keyIdx = '1';
        	var fltmaclist = '';
        	function load_sophia_main1() {
        		var ssid_id = '1';
        var acc_pt_id = '1';
        var wlAuthMode = '34';
        var wlBand = '2';
        var wlBssid = '02:00:00:00:01:02';
        var wlChannel = '0';
        var wlCurrentBw = '3';
        var wlCurrentChannel = '52';
        var wlEnbl = '1';
        var wlHide = '0';
        var wlNBwCap = '3';
        var wlNCtrlsb = '0';
        var wlPhyType = '57';
        var wlSsid = htmlDecode('Casa-5GHz');
        var wlTxPwrRatio = '100';
        var wlWmm = '1';
        var wlWmmUapsd = '1';
        var wlWpaPsk = htmlDecode('senha-de-teste');
        var wlWpsMode = '1';
        var wlMacFlMode = '0';
        	}
        	   var radio_adm_state = '1';
        var ssid_id = '0';
        var acc_pt_id = '0';
        var wifiCurrentChannel = '11';
        </script>
    """.trimIndent()

    val lan = """
        <script nonce='x'>
        	var sessionKey='3333';
        	var lanIp = '192.168.15.1';
        	var dhcpStart = '192.168.15.2';
        	var dhcpEnd = '192.168.15.200';
        </script>
    """.trimIndent()

    fun firewall(ruleIds: String) = """
        <script nonce='x'>
        	var sessionKey='4444';
        	var firewallRuleIndexList='$ruleIds';
        	var firewallInterfaceList='2,1';
        </script>
    """.trimIndent()

    /** The router's own default rule, exactly as `action=show` returns it. */
    const val DEFAULT_RULE = "inst=3,5,1,0,Accept traffic from LAN to HSI WAN,1,-1,2,0,0,1,0,0,-1,*,-1,0,*,-1,0,-1,0,-1,-1,0,-1,-1,0"

    val rebootPopup = """
        <script nonce='x'>
        	var sessionKey='2222';
        </script>
    """.trimIndent()
}
