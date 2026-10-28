param([string]$Ndk = 'D:\AndroidSDK\ndk\30.0.16138531')
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $Ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe'
$assets = Join-Path $PSScriptRoot '../../src/main/assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
$binary = Join-Path $assets 'sbm-diagd'
$buildDir = Join-Path $PSScriptRoot '../../build/native-diagd'
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
$objects = @()
foreach ($source in @('main.c', 'telemetry.c', 'rrc/shannon_rrc.c', 'rrc/rrc_csi.c', 'rrc/rrc_nr_gen.c')) {
    $object = Join-Path $buildDir (([IO.Path]::GetFileNameWithoutExtension($source)) + '.o')
    $warnings = @(if ($source -eq 'rrc/rrc_nr_gen.c') { '-w' } else { '-Wall'; '-Wextra'; '-Werror' })
    & $compiler --target=aarch64-linux-android26 -std=c11 -Os -flto `
        -ffunction-sections -fdata-sections -fstack-protector-strong `
        -D_FORTIFY_SOURCE=2 -DHAVE_TM_GMTOFF @warnings -c (Join-Path $PSScriptRoot $source) -o $object
    if ($LASTEXITCODE -ne 0) { throw "DIAG helper compile failed: $source" }
    $objects += $object
}
& $compiler --target=aarch64-linux-android26 -Os -flto -static @objects `
    '-Wl,--gc-sections' -lm -o $binary
if ($LASTEXITCODE -ne 0) { throw 'DIAG helper build failed' }
& (Join-Path (Split-Path $compiler) 'llvm-strip.exe') $binary
if ($LASTEXITCODE -ne 0) { throw 'DIAG helper strip failed' }
Get-Item $binary | Select-Object FullName, Length
