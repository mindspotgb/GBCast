<#
.SYNOPSIS
  Non-interactive Android device helpers for GBCast (adb + gh). Results are written to test-output\<timestamp>-<command>.
.DESCRIPTION
  Commands: Check, Install, Launch, Stop, Wake, Logcat, Crash, Screenshot, Scroll.
  Install downloads the APK from the latest successful Build GBCast workflow on android-port.
  Use -Serial when several Android devices are connected.
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\gbcast.ps1 Check
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\gbcast.ps1 Logcat -Clear -Seconds 60 -Label "Crazy Taxi"
#>
[CmdletBinding()]
param(
    [Parameter(Position=0,Mandatory=$true)]
    [ValidateSet('Check','Install','Launch','Stop','Wake','Logcat','Crash','Screenshot','Scroll')]
    [string]$Command,
    [string]$Serial = '',
    [int]$Seconds = -1,
    [string]$Label = '',
    [string]$Run = '',
    [switch]$NoLaunch,
    [switch]$Clear,
    [string]$OutDir = ''
)

$Title = 'GBCast'
$Name = 'gbcast'
$Repo = 'mindspotgb/GBCast'
$Branch = 'android-port'
$App = 'app.gbcast.emulator'
$Activity = 'com.flycast.emulator.MainActivity'
$Workflow = 'build.yml'
$LogTags = 'GBCast\S*|GBFrame\S*|GBNative\S*|flycast|AndroidRuntime|DEBUG|libc'
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Fail([string]$Message) {
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}
function Invoke-Adb {
    $full = @()
    if ($Serial) { $full += @('-s', $Serial) }
    $full += $args
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & adb @full } finally { $ErrorActionPreference = $old }
}
function Test-Device {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Fail 'adb not found in PATH (install Android platform-tools).' }
    $state = (Invoke-Adb get-state 2>&1 | Out-String).Trim()
    if ($state -ne 'device') { Fail "Android device missing or unauthorized (adb get-state: $state). Connect by USB and authorize debugging." }
}
function New-OutDir {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $folder = "$stamp-$($Command.ToLower())"
    if ($Label) { $folder += '-' + ($Label -replace '[^A-Za-z0-9]+', '_').Trim('_') }
    if ($OutDir) { $dir = $OutDir } else { $dir = Join-Path $RepoRoot (Join-Path 'test-output' $folder) }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return $dir
}
function Get-DeviceInfo {
    $lines = @()
    $lines += 'device: ' + (Invoke-Adb shell getprop ro.product.manufacturer | Out-String).Trim() + ' ' + (Invoke-Adb shell getprop ro.product.model | Out-String).Trim()
    $lines += 'android: ' + (Invoke-Adb shell getprop ro.build.version.release | Out-String).Trim() + ' (sdk ' + (Invoke-Adb shell getprop ro.build.version.sdk | Out-String).Trim() + ')'
    $lines += 'abi: ' + (Invoke-Adb shell getprop ro.product.cpu.abi | Out-String).Trim()
    $pkg = Invoke-Adb shell dumpsys package $App | Select-String -Pattern 'versionName=|versionCode=|lastUpdateTime=' | ForEach-Object { $_.Line.Trim() }
    if ($pkg) { $lines += "app: $App"; $lines += $pkg } else { $lines += "app: $App NOT INSTALLED" }
    $pids = ((Invoke-Adb shell pidof $App 2>&1) | Out-String).Trim()
    $lines += if ($pids) { "running: pid $pids" } else { 'running: no' }
    return $lines
}
function Start-App {
    Invoke-Adb shell am start -n "$App/$Activity" 2>&1 | Out-Null
}
function Write-OutPath([string]$Dir) { Write-Host "OUT: $Dir" }

switch ($Command) {
    'Check' {
        Test-Device
        Get-DeviceInfo | ForEach-Object { Write-Host $_ }
    }
    'Wake' {
        Test-Device
        Invoke-Adb shell input keyevent KEYCODE_WAKEUP 2>&1 | Out-Null
        Invoke-Adb shell wm dismiss-keyguard 2>&1 | Out-Null
        Write-Host 'Device awake.'
    }
    'Launch' {
        Test-Device
        Start-App
        Write-Host "$Title launched."
    }
    'Stop' {
        Test-Device
        Invoke-Adb shell am force-stop $App 2>&1 | Out-Null
        Write-Host "$Title stopped."
    }
    'Install' {
        Test-Device
        if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail 'GitHub CLI (gh) not found in PATH.' }
        & gh auth status 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail 'gh is not authenticated. Run gh auth login.' }
        if (-not $Run) {
            $Run = (& gh run list -R $Repo --branch $Branch --workflow $Workflow --status success --limit 1 --json databaseId --jq '.[0].databaseId' | Out-String).Trim()
        }
        if (-not $Run) { Fail "No successful $Workflow run found on $Branch." }
        $tmp = Join-Path ([IO.Path]::GetTempPath()) "$Name-apk-$Run"
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        & gh run download $Run -R $Repo -D $tmp
        if ($LASTEXITCODE -ne 0) { Fail "gh run download failed for run $Run." }
        $apk = Get-ChildItem -Path $tmp -Recurse -Filter '*.apk' | Select-Object -First 1
        if (-not $apk) { Fail "No APK in run $Run artifacts." }
        $dir = New-OutDir
        $result = (Invoke-Adb install -r $apk.FullName 2>&1 | Out-String).Trim()
        Set-Content -Path (Join-Path $dir 'install.txt') -Value @("run: $Run", "apk: $($apk.Name)", $result)
        Write-Host $result
        if ($result -notmatch 'Success') { Fail 'Installation failed. Do not uninstall automatically; that could erase app data.' }
        Remove-Item -Recurse -Force $tmp
        if (-not $NoLaunch) { Start-App }
        Write-OutPath $dir
    }
    'Logcat' {
        Test-Device
        $dir = New-OutDir
        if ($Seconds -lt 0) { $Seconds = 30 }
        Set-Content -Path (Join-Path $dir 'device.txt') -Value (Get-DeviceInfo)
        if ($Clear) { Invoke-Adb logcat -c 2>&1 | Out-Null }
        $raw = Join-Path $dir 'logcat-raw.txt'
        if ($Seconds -eq 0) {
            Invoke-Adb logcat -b all -d -v threadtime | Set-Content -Path $raw
        } else {
            $a = @()
            if ($Serial) { $a += @('-s', $Serial) }
            $a += @('logcat', '-b', 'all', '-v', 'threadtime')
            $proc = Start-Process -FilePath 'adb' -ArgumentList $a -RedirectStandardOutput $raw -NoNewWindow -PassThru
            Start-Sleep -Seconds $Seconds
            if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
            Start-Sleep -Milliseconds 800
        }
        $all = @(Get-Content -Path $raw -ErrorAction SilentlyContinue)
        $mine = @($all | Where-Object { $_ -match " $LogTags\s*:" -or $_.Contains($App) })
        $bad = @($mine | Where-Object { $_ -match ' [EF] |FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|ANR in|backtrace:' })
        Set-Content -Path (Join-Path $dir 'logcat-app.txt') -Value $mine
        Set-Content -Path (Join-Path $dir 'logcat-errors.txt') -Value $bad
        Invoke-Adb logcat -b crash -d -v threadtime | Set-Content -Path (Join-Path $dir 'logcat-crash-buffer.txt')
        Write-Host ("lines: {0} total, {1} app, {2} errors/crashes" -f $all.Count, $mine.Count, $bad.Count)
        Write-OutPath $dir
    }
    'Crash' {
        Test-Device
        $dir = New-OutDir
        Invoke-Adb logcat -b crash -d -v threadtime | Set-Content -Path (Join-Path $dir 'crash-buffer.txt')
        foreach ($kind in @('data_app_crash','data_app_native_crash','data_app_anr')) {
            Invoke-Adb shell dumpsys dropbox --print $kind 2>&1 | Select-Object -First 400 | Set-Content -Path (Join-Path $dir "dropbox-$kind.txt")
        }
        Write-OutPath $dir
    }
    'Screenshot' {
        Test-Device
        $dir = New-OutDir
        $remote = "/sdcard/$Name-shot.png"
        Invoke-Adb shell screencap -p $remote 2>&1 | Out-Null
        Invoke-Adb pull $remote (Join-Path $dir 'screen.png') 2>&1 | Out-Null
        Invoke-Adb shell rm $remote 2>&1 | Out-Null
        if (-not (Test-Path (Join-Path $dir 'screen.png'))) { Fail 'Could not capture the screen.' }
        Write-OutPath $dir
    }
    'Scroll' {
        Test-Device
        $dir = New-OutDir
        if ($Seconds -lt 0) { $Seconds = 20 }
        Start-App
        Write-Host "Keep $Title on its library screen and scroll while the measurement runs ($Seconds s)..."
        Invoke-Adb shell dumpsys gfxinfo $App reset 2>&1 | Out-Null
        Start-Sleep -Seconds $Seconds
        $file = Join-Path $dir 'gfxinfo.txt'
        Invoke-Adb shell dumpsys gfxinfo $App | Set-Content -Path $file
        Select-String -Path $file -Pattern 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile' |
            ForEach-Object { $_.Line.Trim() } | Tee-Object -FilePath (Join-Path $dir 'summary.txt') | ForEach-Object { Write-Host $_ }
        Write-OutPath $dir
    }
}
exit 0
