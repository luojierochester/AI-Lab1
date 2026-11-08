$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$exe = Join-Path $root 'bin\npuzzle-viewer.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw '请先运行 build.ps1。' }
$dist = Join-Path $root 'dist'
$package = Join-Path $dist 'NPuzzle-SFML-Windows-x64'
New-Item -ItemType Directory -Path "$package\bin", "$package\examples" -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $root 'bin') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $package 'bin') -Force
}
Copy-Item -LiteralPath (Join-Path $root 'run.cmd') -Destination $package -Force
Copy-Item -LiteralPath (Join-Path $root 'examples\demo.npz') -Destination (Join-Path $package 'examples') -Force
@'
N-Puzzle（SFML 2.6.2 / Windows x64）

1. 双击 run.cmd，打开内置四步演示。演示数据不计入实验性能结果。
2. 点击右上角“打开路径文件”，选择 Java 求解器导出的 .npz 文件。
3. 空格：播放/暂停；左右箭头：单步；Home：复位；上下箭头：调速；End：终点；O：打开文件。
4. 可点击下方按钮和进度条。窗口调整大小时保持棋盘比例。
5. 命令行：run.cmd 路径文件.npz；或 bin\npuzzle-viewer.exe --validate 路径文件.npz。
6. 程序加载文件时会校验每个棋盘的数字、步数和相邻移动。坏文件不会替换当前路径。
7. 内置中文字体使用 Windows 微软雅黑，不分发系统字体。若系统缺少中文字体，可用 --font 指定字体文件。

本程序只播放 Java 求解器输出的路径，不运行搜索。请将求解耗时与播放动画时间分开。
程序使用 SFML 和 FreeType，许可证及第三方说明位于 bin 目录。
'@ | Set-Content -LiteralPath (Join-Path $package '使用说明.txt') -Encoding UTF8
$zip = Join-Path $dist 'NPuzzle-SFML-Windows-x64.zip'
Compress-Archive -LiteralPath $package -DestinationPath $zip -Force
Get-Item -LiteralPath $zip | Select-Object FullName, Length
Get-FileHash -LiteralPath $zip -Algorithm SHA256
