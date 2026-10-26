# 可视化依赖与构建来源

本项目的 `src/main.cpp`、`src/path.hpp` 是为本实验编写的可视化程序。程序使用 SFML 绘制窗口、文字、棋盘和动画，不含第三方 N-Puzzle 求解器。

| 依赖 | 固定版本 | 官方来源 | 说明 |
| --- | --- | --- | --- |
| SFML | 2.6.2 | https://www.sfml-dev.org/files/SFML-2.6.2-sources.zip | 使用源码构建 Graphics、Window、System；许可证 zlib/png |
| CMake | 3.31.10 | https://github.com/Kitware/CMake/releases/tag/v3.31.10 | Windows 便携工具，仅参与构建 |
| MinGW-w64 GCC | 本机验证为 8.1.0，x86_64-posix-seh | 使用已经安装的工具链 | 用户可通过 `-Cxx` 指定兼容工具链 |
| FreeType | SFML 源码随附版本 | SFML `extlibs` | 字体渲染使用 FreeType，受 FreeType Project License 约束 |
| GCC 标准运行库 / winpthreads / MinGW-w64 CRT | 本机 GCC 8.1.0 工具链随附版本 | 本机工具链 `licenses` 目录 | 静态链接；保留 GCC GPLv3 及 Runtime Library Exception、winpthreads、MinGW-w64 许可文本 |
| 中文字体 | 使用操作系统已安装字体 | Windows 优先 `C:/Windows/Fonts/msyh.ttc` | 不复制、不分发系统字体；可用 `--font` 指定有授权的字体 |

下载校验：

- SFML 源码 ZIP SHA-256：`19d6dbd9c901c74441d9888c13cb1399f614fe8993d59062a72cfbceb00fed04`
- CMake Windows x64 ZIP SHA-256：`13d1a463d7130df5339baedd63d8ae990aaf385062b2f42f372796143ae94086`，已与官方 `cmake-3.31.10-SHA-256.txt` 比对。

工具、下载归档和构建缓存不提交 Git。首次构建需要联网获取这些固定依赖，以后可使用本地缓存。CMake 的 SFML 下载包含固定 SHA-256 校验。Windows 构建脚本使用独立 TEMP 子目录及目录联接解决旧 MinGW 对中文路径的兼容问题，不移动项目文件、不修改全局 PATH、不安装系统软件。仓库中的 Windows x64 可执行文件已经静态链接；经 `objdump -p` 检查，仅依赖 Windows 自带 DLL，无须队友另装 SFML 或 MinGW。

程序使用 SFML 的功能符合官方文档：https://www.sfml-dev.org/tutorials/2.6/getting-started/build-from-source/ 。分发可执行文件时应保留 `licenses` 中随构建复制至 `bin` 的全部许可文本。FreeType 许可原文来源：https://raw.githubusercontent.com/freetype/freetype/master/docs/FTL.TXT 。字体不随包分发。
