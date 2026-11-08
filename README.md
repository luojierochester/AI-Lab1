# 人工智能实验1：N-Puzzle 搜索与 SFML 可视化

基于课程提供的 Java 框架，完成八数码与十五数码的 A*、IDA*、不相交模式数据库和 SFML 解路径演示。本仓库提供源码、可执行演示、真实实验数据、正确性验证与队友交接说明；**暂未撰写实验研究报告**。

## 从这里开始

| 你想做什么 | 入口 |
|---|---|
| 查看课程要求和对应实现 | [要求对照](docs/REQUIREMENTS.md) |
| 查看课程汇报 PPT | [课程汇报](presentation/N-Puzzle实验一-课程汇报.pptx) |
| 准备上台讲解 | [逐页讲稿与答疑](presentation/实验一汇报讲稿.md) · [Word 版](presentation/实验一汇报讲稿.docx)，按约 8 分钟安排，附讲前导读和操作提示 |
| 查看最新版 20 秒操作录屏 | [播放、单步与跳到终点](presentation/media/sfml-playback-demo-centered.mp4)，已调整标题与竖线的垂直对齐；本版尚未嵌入 PPT |
| 查看 PPT 中的录屏 | [原版录屏](presentation/media/sfml-playback-demo.mp4)，已嵌入 PPT 第 14 页，放映时单击播放 |
| 直接观看解路径 | Windows：`visualizer/bin/npuzzle-viewer.exe`，或运行 `visualizer/run.cmd` |
| 切换其他课程题目 | [演示案例](visualizer/examples/README.md)：3 个八数码、7 个十五数码的真实解路径 |
| 编译并运行 Java 搜索 | 下方“快速运行”；只需 JDK 17 或更高版本 |
| 查看真实测量结果 | [results](results/)、[测量方法](docs/EXPERIMENTS.md) |
| 准备后续实验报告 | [算法实现说明](docs/ALGORITHMS.md)、原始 CSV/JSON、SFML 截图 |
| 修改实现或做后续实验 | `src/stud/g01/`、`visualizer/src/`，保持 `src/core/` 不变 |

## 快速运行

在仓库根目录打开 PowerShell/终端。Java 部分没有 Maven、Gradle 或其他第三方依赖。

```powershell
# 1. 从当前源码重新编译，不使用原教学框架遗留的 .class。
java scripts/Build.java

# 2. A* 求解指定八数码，导出路径。
java -Xmx4g -cp build/classes stud.g01.runner.LabRunner solve --input resources/problems.txt --case 3 --algorithm astar --heuristic manhattan --output results/my-eight.npz

# 3. 用真正的 SFML 程序播放结果。
.\visualizer\bin\npuzzle-viewer.exe .\results\my-eight.npz

# 4. IDA* + PDB 求解指定困难十五数码。
java -Xmx4g -cp build/classes stud.g01.runner.LabRunner solve --input resources/problems3.txt --case 10 --algorithm ida --heuristic pdb --timeout 60 --nodes 1000000000 --output results/my-hard15.npz
```

也可用 `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 solve ...` 或 `sh run.sh solve ...`，脚本会先编译。

`Build.java` 在临时构建目录将原框架的 GBK/新源码的 UTF-8 统一为 UTF-8 再编译，**不修改原始文件字节**。已有 `out/`、IDE 配置和旧清单不会参与构建。代码以 Java 17 为兼容目标，本地实测使用 JDK 21。

## 四个阶段

| 阶段 | 实现 | 验证方式 |
|---|---|---|
| 1：A* + 优先队列 | 复用课程 `BestFirstSearcher`；索引堆去重与更新；错位数、曼哈顿启发函数 | 两个规定八数码实例的重复计时与精确最优解对照 |
| 2：IDA* | 阈值迭代加深、原地移动、逆动作剪枝、有界置换表 | 课程十五数码及有限深度独立 BFS 对照 |
| 3：不相交 PDB | 默认 6-6-3 放松加性数据库，增量查表，转置估值取最大值 | 规定困难实例；单列准备、搜索、总耗时 |
| 4：SFML 可视化 | 独立 C++ 程序读取 NPZ1 路径，动画与播放控制 | 文本解析/播放测试、路径逐步检查、实际界面截图 |

老师的 26 个已有 Java 框架文件保持原样，测试使用 SHA-256 核查。旧框架引用 `stud.solver.IdAStar` 的问题由新增适配器解决，无需改写核心类。

### 本机验收结果

测量环境：2026-10-26，Windows，Intel Core i9-14900HX，约 32 GB 内存，Liberica JDK 21.0.11，Java 堆上限 4 GB。时间取本机实测，不保证不同机器得到相同数字。

| 验收项 | 实际结果 | 原始证据 |
|---|---|---|
| 两个规定八数码，A* + 错位数 | 每题 5 次均成功；所有测量最大 **0.2055 秒**，解均为 31 步 | `results/course-eight.csv` |
| 两个规定八数码，A* + 曼哈顿 | 每题 5 次均成功；所有测量最大 **0.0105 秒** | `results/course-eight.csv` |
| 指定困难十五数码，首次建库 | **70 步**；准备 1.0108 秒，搜索 39.9071 秒，准备至核验总计 **40.9251 秒** | `results/hard15-cold.json` |
| 相同难例，已有磁盘缓存 | 两次总计 **37.9481 / 36.9913 秒** | `results/hard15-warm-1.json`、`hard15-warm-2.json` |
| 课程全部 7 个十五数码实例 | 均求解并逐步核验，长度依次为 **52、51、56、57、50、61、70** | `results/course-fifteen-pdb.csv` |

对比实验保存 **209 条原始 CSV 记录**：208 条成功，1 条为较弱基线在生成实例上的 `NODE_LIMIT`，保留失败记录且不计作成功耗时。另保存上述 3 次困难实例独立启动测量。[汇总数据](results/summary.json)覆盖 63 个配置组。

## 可视化使用

Windows 仓库附带约 4 MB 的静态链接 SFML 可执行文件，运行不需要安装 SFML 或编译器。无参数运行显示带明确标记的内置短演示；可在窗口中打开 Java 导出的 `.npz` 文件。

点击“打开路径文件”，默认进入 `visualizer/examples/`。这里已整理课程全部 10 题的解路径，文件名中的 `case` 是原始题号，`steps` 是解的步数。例如 `8puzzle-case02-31steps.npz` 是第 2 题八数码，`15puzzle-case10-70steps.npz` 是第 10 题十五数码。[案例列表](visualizer/examples/README.md)列有原题和求解输出来源；Windows 便携包也包含这些文件。选过文件后，下一次打开会回到上次所选文件的文件夹。

功能包括播放/暂停、前后单步、复位、速度调节、进度跳转和当前步信息。Java 的动作方向指空格移动；界面显示移动滑块的编号和方向，两者方向相反。完整路径在载入时校验，非法文件不会被当作有效答案播放。

![SFML 播放指定困难实例](visualizer/screenshots/hard15-start.png)

界面使用系统中文字体（Windows 优先微软雅黑），字体文件不随仓库重新分发。源代码与依赖许可证在 `visualizer/`；源码固定使用官方 SFML 2.6.2，Windows 构建脚本固定下载版本和 SHA-256，不安装全局软件。

```powershell
# 若需要重新构建 Windows 可视化，先准备 MinGW g++。
powershell -NoProfile -ExecutionPolicy Bypass -File visualizer/build.ps1
# 无窗口检查与文件校验。
.\visualizer\bin\npuzzle-viewer.exe --self-test
.\visualizer\bin\npuzzle-viewer.exe --validate .\results\my-hard15.npz
```

Linux 可安装 SFML 开发包，使用：

```sh
cmake -S visualizer -B build/visualizer -DNPZ_BUILD_SFML=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build/visualizer --parallel 2
ctest --test-dir build/visualizer --output-on-failure
```

## 一键核查正确性

```powershell
java scripts/Build.java
java -Xmx4g -cp build/classes stud.g01.runner.LabRunner verify
java -Xmx4g -cp build/classes stud.g01.tests.AdvancedChecks
.\visualizer\bin\npuzzle-viewer.exe --self-test
```

- 八数码：独立 BFS 枚举 **181,440 个状态、483,840 条有向边**；三种启发函数全部检查可采纳性和一致性；215 次求解与精确距离比较。
- 基础模型：2×2 全部 576 对起终点、不可变性、输入异常、随机队列操作等 **38,856 项检查**。
- 十五数码补充：标准目标深度 10 的 **3,754 个状态**、两个任意目标深度 8 的有限状态集；增量估值、TT 冲突、时间/节点限额等 **88,984 项检查、187 次最优搜索对照**。
- 这些穷举范围不等于穷举所有十五数码。大实例的最优性还依赖启发函数与 IDA* 的理论条件；每条导出路径另做独立合法性和目标检查。
- GitHub Actions 会在 Linux/JDK 17 构建并复跑 Java 与 SFML 无窗口测试。云端 CI 与本地验证状态分别记录。

重新运行验证会将当前 JSON 写入 `build/verification.json`，不会覆盖仓库中保留的验收数据。

## 性能比较与复现

```powershell
# 八数码，A*/IDA* × 三种启发函数，每个配置重复5次。
java -Xmx4g -cp build/classes stud.g01.runner.LabRunner benchmark --input resources/problems.txt --size 3 --repeats 5 --output results/my-eight.csv
# 比较 Open 表实现；同一完整排序规则控制变量。
java -Xmx4g -cp build/classes stud.g01.runner.LabRunner benchmark --input resources/problems.txt --size 3 --repeats 3 --algorithms astar --heuristics manhattan,pdb --frontiers heap,list --output results/my-frontiers.csv
# 将原始数据汇总；Python只用于这个可选汇总，不是Java运行依赖。
py scripts/summarize.py results/my-eight.csv results/my-frontiers.csv --output results/my-summary.json
```

参数：`--algorithm astar|ida`，`--heuristic misplaced|manhattan|pdb`，`--frontier heap|list`；`--case` 从 1 开始。`benchmark` 使用复数形式 `--algorithms`、`--heuristics`、`--frontiers` 接受逗号分隔列表。

CSV 保存每次结果；同名 `.meta.json` 保存完整参数、输入散列、运行环境与算法开关。输出状态严格区分成功、无解、超时与节点限制。失败时不会导出伪解；若指定输出位置已有旧路径，将改名保留为 `.stale-时间戳`，避免误看成新结果。

完整复测可运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-experiments.ps1`；默认写入 `results/reproduced`，保留当前验收数据。单次难例可能耗时几十秒，完整流程需要数分钟。IDA* 不使用传入的 Frontier，CSV 中该列仅为接口配置，`peak_frontier=-1` 表示不适用。

PDB 默认缓存于 `.cache/pdb`，有效条目 **11,534,880**，查找数组约 **32.004 MiB**；默认置换表原始数组约 **28 MiB**。缓存可删除后重建，不提交 Git。`benchmark` 使用热缓存；冷启动需用新缓存目录单独 `solve`，详见[计时口径与消融命令](docs/EXPERIMENTS.md)。

## 目录与队友分工入口

```text
src/core/                  老师给定框架，原样保留
src/stud/g01/problem/      棋盘、动作、可解性、问题模型
src/stud/g01/queue/        索引堆 Frontier
src/stud/g01/heuristic/    三种启发函数和 PDB 构建/缓存
src/stud/g01/solver/       IDA* 与置换表
src/stud/g01/runner/       课程 Feeder、命令行实验入口
src/stud/g01/tests/        独立 BFS、模型/算法与边界验证
visualizer/               SFML 源码、Windows程序、构建与测试
resources/                课程数据与固定种子生成的数据
results/                  原始实验数据、路径、验证记录和机器信息
verification/             课程框架散列、数据生成参数
docs/                     要求对应、实现说明和测量方法
```

后续报告撰写可直接引用原始数据并重新生成图表；理论与实际实现的差异（尤其是放松 PDB）要如实说明。新增算法先补验证，再运行相同数据集对比。请勿将未测量的新代码与旧结果混用。

## 依赖与分发

课程原始代码保留原有注释和来源；本仓库不对其重新声明开源许可证。SFML、FreeType 和工具链相关说明见 [第三方说明](visualizer/THIRD_PARTY.md)。测试数据与测量结果来自本次实际运行，未使用硬编码解答替代搜索。
