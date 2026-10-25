# 本地 ClientHello 抓取：监听 127.0.0.1:18443（adb reverse 转发自手机），
# 读取一条 TLS ClientHello，解析密码套件列表与顺序，与 Chrome 移动端期望序列比对。
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 18443)
$listener.Start()
Write-Output "listening on 18443..."
try {
    $client = $listener.AcceptTcpClient()   # 阻塞等第一条连接
    $stream = $client.GetStream()
    $stream.ReadTimeout = 15000
    $buf = New-Object byte[] 16384
    $n = $stream.Read($buf, 0, $buf.Length)
    Write-Output ("received {0} bytes" -f $n)
    if ($n -gt 5 -and $buf[0] -eq 0x16) {
        $recVer = '{0:x2}{1:x2}' -f $buf[1], $buf[2]
        $hsType = $buf[5]
        $p = 6
        $chLen = ($buf[$p] -shl 16) + ($buf[$p+1] -shl 8) + $buf[$p+2]
        $p += 3
        $clientVer = '{0:x2}{1:x2}' -f $buf[$p], $buf[$p+1]
        $p += 2 + 32
        $sidLen = $buf[$p]; $p += 1 + $sidLen
        $csLen = ($buf[$p] -shl 8) + $buf[$p+1]; $p += 2
        $suites = @()
        for ($i = 0; $i -lt [int]($csLen / 2); $i++) { $suites += ('{0:x4}' -f (($buf[$p] -shl 8) + $buf[$p+1])); $p += 2 }
        $p += 1  # compression methods length byte
        $compLen = $buf[$p-1]
        $p += $compLen
        $extLen = if ($p + 1 -lt $n) { ($buf[$p] -shl 8) + $buf[$p+1] } else { 0 }

        Write-Output ("recordVer=0x{0} handshakeType=0x{1:x2} clientVer=0x{2} cipherBytes={3} extBytes={4}" -f $recVer, $hsType, $clientVer, $csLen, $extLen)
        Write-Output ("cipherSuites({0}): {1}" -f $suites.Count, ($suites -join ' '))

        $expect = @('1301','1302','1303','c02b','c02f','c02c','c030','cca9','cca8','c013','c014','009c','009d','002f','0035')
        $matched = 0
        for ($i = 0; $i -lt [Math]::Min($expect.Count, $suites.Count); $i++) {
            if ($expect[$i] -eq $suites[$i]) { $matched++ } else { break }
        }
        Write-Output ("expect-prefix-match: {0}/{1}  (前缀完全一致 = Chrome 顺序)" -f $matched, $expect.Count)
    } else {
        Write-Output "not a TLS handshake record"
    }
    try { $stream.Close(); $client.Close() } catch {}
} finally {
    $listener.Stop()
}
Write-Output "CAPTURE_DONE"
