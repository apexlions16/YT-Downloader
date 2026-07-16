param(
    [string]$CiktiArsivi = "desktopApp/src/main/resources/windows-motor.zip",
    [string]$GunlukYolu = "windows-motor.log"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Start-Transcript -Path $GunlukYolu -Force | Out-Null

try {
    $headers = @{
        Authorization = "Bearer $env:GITHUB_TOKEN"
        Accept = "application/vnd.github+json"
        "User-Agent" = "BPC-GitHub-Actions"
    }
    $motor = Join-Path $PWD "windows-motor-hazir"
    $gecici = Join-Path $PWD "windows-motor-gecici"
    Remove-Item $motor, $gecici -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $motor, $gecici | Out-Null

    Write-Host "[1/4] yt-dlp Nightly indiriliyor ve doğrulanıyor..."
    $ytDlp = Join-Path $motor "yt-dlp.exe"
    Invoke-WebRequest -Headers $headers -Uri "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp.exe" -OutFile $ytDlp
    $toplamMetni = (Invoke-WebRequest -Headers $headers -Uri "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/SHA2-256SUMS").Content
    $toplamSatiri = ($toplamMetni -split "`r?`n") | Where-Object { $_ -match "\syt-dlp\.exe\s*$" } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($toplamSatiri)) { throw "SHA2-256SUMS içinde yt-dlp.exe bulunamadı" }
    $beklenen = ($toplamSatiri.Trim() -split "\s+")[0].ToLowerInvariant()
    $gercek = (Get-FileHash -Algorithm SHA256 $ytDlp).Hash.ToLowerInvariant()
    if ($gercek -ne $beklenen) { throw "yt-dlp SHA-256 doğrulaması başarısız: beklenen=$beklenen gerçek=$gercek" }

    Write-Host "[2/4] FFmpeg ve FFprobe indiriliyor..."
    $ffSurum = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/yt-dlp/FFmpeg-Builds/releases/latest"
    $ffVarliklar = @($ffSurum.assets | Where-Object { $_.name -match "win64.*gpl.*\.zip$" -and $_.name -notmatch "shared" })
    $ffVarlik = $ffVarliklar | Sort-Object { if ($_.name -match "master-latest") { 0 } else { 1 } } | Select-Object -First 1
    if (-not $ffVarlik) {
        Write-Host "Mevcut FFmpeg varlıkları:"
        $ffSurum.assets.name | ForEach-Object { Write-Host " - $_" }
        throw "Uygun FFmpeg Windows paketi bulunamadı"
    }
    Write-Host "FFmpeg varlığı: $($ffVarlik.name)"
    $ffArsiv = Join-Path $gecici $ffVarlik.name
    Invoke-WebRequest -Headers $headers -Uri $ffVarlik.browser_download_url -OutFile $ffArsiv
    if ($ffVarlik.digest -and $ffVarlik.digest.StartsWith("sha256:")) {
        $beklenenFf = $ffVarlik.digest.Substring(7).ToLowerInvariant()
        $gercekFf = (Get-FileHash -Algorithm SHA256 $ffArsiv).Hash.ToLowerInvariant()
        if ($gercekFf -ne $beklenenFf) { throw "FFmpeg SHA-256 doğrulaması başarısız" }
    }
    $ffCikarma = Join-Path $gecici "ffmpeg"
    Expand-Archive -Path $ffArsiv -DestinationPath $ffCikarma -Force
    $ffmpegDosyasi = Get-ChildItem $ffCikarma -Recurse -Filter ffmpeg.exe | Select-Object -First 1
    $ffprobeDosyasi = Get-ChildItem $ffCikarma -Recurse -Filter ffprobe.exe | Select-Object -First 1
    if (-not $ffmpegDosyasi -or -not $ffprobeDosyasi) { throw "FFmpeg arşivinde ffmpeg.exe veya ffprobe.exe bulunamadı" }
    Copy-Item $ffmpegDosyasi.FullName (Join-Path $motor "ffmpeg.exe")
    Copy-Item $ffprobeDosyasi.FullName (Join-Path $motor "ffprobe.exe")

    Write-Host "[3/4] Deno indiriliyor..."
    $denoSurum = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/denoland/deno/releases/latest"
    $denoVarlik = $denoSurum.assets | Where-Object { $_.name -eq "deno-x86_64-pc-windows-msvc.zip" } | Select-Object -First 1
    if (-not $denoVarlik) { throw "Deno Windows paketi bulunamadı" }
    $denoArsiv = Join-Path $gecici $denoVarlik.name
    Invoke-WebRequest -Headers $headers -Uri $denoVarlik.browser_download_url -OutFile $denoArsiv
    $denoCikarma = Join-Path $gecici "deno"
    Expand-Archive -Path $denoArsiv -DestinationPath $denoCikarma -Force
    $denoDosyasi = Get-ChildItem $denoCikarma -Recurse -Filter deno.exe | Select-Object -First 1
    if (-not $denoDosyasi) { throw "Deno arşivinde deno.exe bulunamadı" }
    Copy-Item $denoDosyasi.FullName (Join-Path $motor "deno.exe")

    Write-Host "[4/4] MPV oynatıcı indiriliyor..."
    $mpvSurum = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest"
    $mpvVarliklar = @($mpvSurum.assets | Where-Object { $_.name -match "^mpv-x86_64-.*\.7z$" -and $_.name -notmatch "dev|v3" })
    $mpvVarlik = $mpvVarliklar | Select-Object -First 1
    if (-not $mpvVarlik) {
        Write-Host "Mevcut MPV varlıkları:"
        $mpvSurum.assets.name | ForEach-Object { Write-Host " - $_" }
        throw "MPV x86_64 Windows paketi bulunamadı"
    }
    Write-Host "MPV varlığı: $($mpvVarlik.name)"
    $mpvArsiv = Join-Path $gecici $mpvVarlik.name
    Invoke-WebRequest -Headers $headers -Uri $mpvVarlik.browser_download_url -OutFile $mpvArsiv
    if ($mpvVarlik.digest -and $mpvVarlik.digest.StartsWith("sha256:")) {
        $beklenenMpv = $mpvVarlik.digest.Substring(7).ToLowerInvariant()
        $gercekMpv = (Get-FileHash -Algorithm SHA256 $mpvArsiv).Hash.ToLowerInvariant()
        if ($gercekMpv -ne $beklenenMpv) { throw "MPV SHA-256 doğrulaması başarısız" }
    }
    $mpvCikarma = Join-Path $gecici "mpv"
    New-Item -ItemType Directory -Force -Path $mpvCikarma | Out-Null
    & 7z.exe x $mpvArsiv "-o$mpvCikarma" -y | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "MPV arşivi çıkarılamadı: 7z çıkış kodu $LASTEXITCODE" }
    $mpvDosyasi = Get-ChildItem $mpvCikarma -Recurse -Filter mpv.exe | Select-Object -First 1
    if (-not $mpvDosyasi) { throw "MPV arşivinde mpv.exe bulunamadı" }
    Copy-Item $mpvDosyasi.FullName (Join-Path $motor "mpv.exe")

    $zorunlu = @("yt-dlp.exe", "ffmpeg.exe", "ffprobe.exe", "deno.exe", "mpv.exe")
    foreach ($dosya in $zorunlu) {
        $yol = Join-Path $motor $dosya
        if (-not (Test-Path $yol)) { throw "Motor paketi eksik: $dosya" }
        $boyut = (Get-Item $yol).Length
        Write-Host "$dosya hazır: $boyut bayt"
    }

    $hedefArsiv = Join-Path $PWD $CiktiArsivi
    New-Item -ItemType Directory -Force -Path (Split-Path $hedefArsiv -Parent) | Out-Null
    if (Test-Path $hedefArsiv) { Remove-Item $hedefArsiv -Force }
    Compress-Archive -Path (Join-Path $motor "*") -DestinationPath $hedefArsiv -CompressionLevel Optimal

    Get-ChildItem $motor | ForEach-Object {
        "$($_.Name) $((Get-FileHash -Algorithm SHA256 $_.FullName).Hash.ToLowerInvariant())"
    } | Set-Content -Encoding UTF8 windows-motor-sha256.txt

    Write-Host "Windows motor paketi hazır: $hedefArsiv"
}
finally {
    Stop-Transcript | Out-Null
}
