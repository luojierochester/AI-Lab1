param([string]$Cxx = '', [int]$Jobs = 4)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not $Cxx) {
    $found = Get-Command g++.exe -ErrorAction SilentlyContinue
    if ($found) { $Cxx = $found.Source }
}
if (-not $Cxx -or -not (Test-Path -LiteralPath $Cxx)) {
    throw '需要 MinGW-w64 g++。请先将其加入 PATH，或运行 build.ps1 -Cxx C:\mingw64\bin\g++.exe'
}
$compilerDir = Split-Path -Parent $Cxx
$make = Join-Path $compilerDir 'mingw32-make.exe'
$cc = Join-Path $compilerDir 'gcc.exe'
if (-not (Test-Path -LiteralPath $make)) { throw '找不到与 g++ 同目录的 mingw32-make.exe。' }

# GCC 8/MinGW Make cannot reliably consume Unicode build paths. Keep source
# files in place and use a task-specific ASCII junction in TEMP for the build.
$sha = [System.Security.Cryptography.SHA256]::Create()
$key = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($root)))).Replace('-', '').Substring(0, 12)
$cacheBase = if ($env:NPZ_BUILD_ROOT) { $env:NPZ_BUILD_ROOT } else { Join-Path $env:TEMP 'NPuzzleSFML' }
$cache = Join-Path $cacheBase $key
if ($cache -match '[^\x00-\x7F]') { throw '临时构建路径含非 ASCII 字符，请将 NPZ_BUILD_ROOT 设为如 C:\Temp\NPuzzleSFML。' }
New-Item -ItemType Directory -Path $cache -Force | Out-Null
$source = Join-Path $cache 'source'
if (-not (Test-Path -LiteralPath $source)) {
    New-Item -ItemType Junction -Path $source -Target $root | Out-Null
} else {
    $link = Get-Item -LiteralPath $source
    if (($link.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -or $link.Target -ne $root) {
        throw "临时目录已存在非预期内容，拒绝使用：$source"
    }
}

$tools = Join-Path $cache 'tools'
New-Item -ItemType Directory -Path $tools -Force | Out-Null
$existingTools = Join-Path $source 'tools'
$existingCmake = Join-Path $existingTools 'cmake-3.31.10-windows-x86_64'
if ((Test-Path -LiteralPath $existingCmake) -and -not (Test-Path -LiteralPath (Join-Path $tools 'cmake-3.31.10-windows-x86_64'))) {
    Copy-Item -LiteralPath $existingCmake -Destination $tools -Recurse
}
$cmake = Join-Path $tools 'cmake-3.31.10-windows-x86_64\bin\cmake.exe'
if (-not (Test-Path -LiteralPath $cmake)) {
    $archive = Join-Path $tools 'cmake-3.31.10-windows-x86_64.zip'
    $url = 'https://github.com/Kitware/CMake/releases/download/v3.31.10/cmake-3.31.10-windows-x86_64.zip'
    if (-not (Test-Path -LiteralPath $archive)) { Invoke-WebRequest -Uri $url -OutFile $archive }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne '13D1A463D7130DF5339BAEDD63D8AE990AAF385062B2F42F372796143AE94086') {
        throw 'CMake ZIP 的 SHA-256 不符，拒绝执行。请检查或删除 tools 下的损坏下载。'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
}
$build = Join-Path $cache 'build'
$argsForCmake = @('-S', $source, '-B', $build, '-G', 'MinGW Makefiles', '-DCMAKE_BUILD_TYPE=Release',
    "-DCMAKE_CXX_COMPILER=$Cxx", "-DCMAKE_C_COMPILER=$cc", "-DCMAKE_MAKE_PROGRAM=$make")
$localSfml = Join-Path $existingTools 'SFML-2.6.2'
if (Test-Path -LiteralPath $localSfml) { $argsForCmake += "-DFETCHCONTENT_SOURCE_DIR_SFML=$localSfml" }
& $cmake @argsForCmake
if ($LASTEXITCODE) { throw "CMake 配置失败：$LASTEXITCODE" }
& $cmake --build $build --parallel $Jobs
if ($LASTEXITCODE) { throw "编译失败：$LASTEXITCODE" }
& $cmake --build $build --target test
if ($LASTEXITCODE) { throw "测试失败：$LASTEXITCODE" }
$bin = Join-Path $root 'bin'
New-Item -ItemType Directory -Path $bin -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $build 'npuzzle-viewer.exe') -Destination $bin -Force
Copy-Item -LiteralPath (Join-Path $source 'THIRD_PARTY.md') -Destination $bin -Force
Get-ChildItem -LiteralPath (Join-Path $source 'licenses') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $bin -Force
}
Write-Host "构建与自测通过：$bin\npuzzle-viewer.exe"
