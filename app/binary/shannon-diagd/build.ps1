param([string]$Ndk = 'D:\AndroidSDK\ndk\30.0.16138531')
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $Ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe'
$assets = Join-Path $PSScriptRoot '../../src/main/assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
$binary = Join-Path $assets 'sbm-diagd'
& $compiler --target=aarch64-linux-android26 -std=c11 -O2 -static `
    -Wall -Wextra -Werror -fstack-protector-strong -D_FORTIFY_SOURCE=2 `
    (Join-Path $PSScriptRoot 'main.c') (Join-Path $PSScriptRoot 'telemetry.c') -lm -o $binary
if ($LASTEXITCODE -ne 0) { throw 'DIAG helper build failed' }
& (Join-Path (Split-Path $compiler) 'llvm-strip.exe') $binary
if ($LASTEXITCODE -ne 0) { throw 'DIAG helper strip failed' }
Get-Item $binary | Select-Object FullName, Length
