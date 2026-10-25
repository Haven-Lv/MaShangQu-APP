# 自动安装 Android SDK 命令行工具链到 D:\Software\Android\Sdk
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Software\jdks\jdk-21.0.11'
$sdk = 'D:\Software\Android\Sdk'
$zip = "$env:TEMP\cmdline-tools.zip"
$url = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'

New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null

if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Write-Output "下载 cmdline-tools ..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 300
    } catch {
        Write-Output "直连失败（$($_.Exception.Message)），改走 Clash 代理 ..."
        $p = New-Object System.Net.WebProxy('http://127.0.0.1:7897')
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -Proxy $p -TimeoutSec 600
    }
    Write-Output "解压 ..."
    Expand-Archive -Path $zip -DestinationPath "$sdk\cmdline-tools\__tmp" -Force
    Move-Item "$sdk\cmdline-tools\__tmp\cmdline-tools" "$sdk\cmdline-tools\latest"
    Remove-Item "$sdk\cmdline-tools\__tmp" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
}

$sdkm = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
Write-Output "接受许可 ..."
0..20 | ForEach-Object { 'y' } | & cmd /c "`"$sdkm`" --licenses" | Out-Null

Write-Output "安装 platform-tools / platforms;android-36 / build-tools;34.0.0 ..."
& cmd /c "`"$sdkm`" platform-tools `"platforms;android-36`" `"build-tools;34.0.0`"" | Select-Object -Last 5

Write-Output "=== 校验 ==="
@('platform-tools\adb.exe', 'platforms\android-36\android.jar', 'build-tools\34.0.0\aapt2.exe') | ForEach-Object {
    $ok = Test-Path "$sdk\$_"
    Write-Output ("{0} : {1}" -f $_, $(if ($ok) { 'OK' } else { '缺失' }))
}
Write-Output "SDK_SETUP_DONE"
