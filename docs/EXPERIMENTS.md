# 实验复现与数据解释

## 数据与基线

- `resources/problems.txt`：课程原始 4 个实例，3 个八数码、1 个十五数码。
- `resources/problems3.txt`：课程原始 10 个实例，3 个八数码、7 个十五数码；第 10 个为指定倒序难例。
- `resources/generated.txt`：固定种子生成的 8 个实例；生成参数在 `verification/generated-dataset.json`。随机游走步数只是生成过程长度，不能当作最短解长度。
- `results/environment.json`：本次本地测量环境、JDK、内存设置及输入文件 SHA-256。成绩验收须考虑教师机器差异。

## 指标口径

| 字段 | 定义 |
|---|---|
| `status` | SOLVED 成功；UNSOLVABLE 奇偶性证明无解；TIMEOUT/NODE_LIMIT 仅表示资源限制 |
| `moves` | 结果路径中的合法移动数，不含初始棋盘；失败为 -1 |
| `initial_h` | 初始启发式估计，用于与真实最短长度对比 |
| `wall_seconds` | 从调用 search 到返回的单调时钟耗时；包括搜索方法内分配与准备 |
| `cpu_seconds` | 当前搜索线程 CPU 时间，与墙钟时间不同 |
| `preparation_seconds` | CLI 调用搜索之前准备初始估值的耗时，可能包括 PDB 生成或磁盘加载 |
| `total_seconds` | 本次求解调用中的准备、对象建立、搜索和路径核验总耗时；不含 JVM 启动、编译和输出写盘 |
| `generated` | 搜索产生的后继数；IDA* 不统计立即逆移分支，不能直接等同于 A* 的生成口径 |
| `expanded` | 实际展开计数；IDA* 包含跨轮重复展开，因此不等于唯一状态数 |
| `peak_frontier` | A* 的逻辑 Open 表峰值；IDA* 为 -1，表示不适用，不能写成零内存 |
| `path_verified` | 独立逐步比较棋盘数组，核对相邻空格交换、路径代价及最终目标 |

CSV 保留每次测量，不只保留最快一次。汇总脚本提供中位数、最小、最大值和成功次数。失败记录不进入成功耗时中位数，必须同时呈现失败状态。

`benchmark` 在每个算法/启发函数/队列组合之前运行一个简单实例，减少一次性初始化影响；这不代表所有 JVM 代码已经充分预热。保留重复测量的离散程度，不用单次最小值宣称普遍性能。

`--timeout` 约束搜索阶段，不含 PDB 构建。IDA* 定期检查时钟，因此存在少量检查间隔误差；A* 每次出队检查时钟。`--nodes` 对 A* 限制出队数，对 IDA* 限制展开数。资源受限结果是被截断的观测，不能据此声称算法无法求解。

## 如何做冷启动与消融

冷启动必须使用新的缓存目录，不要删除正在使用的缓存。例如 PowerShell：

```powershell
java '-Dlab.pdbCache=.cache/cold-example' -Xmx4g -cp build/classes stud.g01.runner.LabRunner solve --input resources/problems3.txt --case 10 --algorithm ida --heuristic pdb --timeout 60 --output results/cold-hard15.npz
```

第二次执行同样命令是磁盘缓存已存在的启动；同一 JVM 的多次 benchmark 还会复用内存缓存。三者应分别说明。

```powershell
# 关闭对称估值，观察减少一次查表能否抵消更多节点。
java '-Dlab.pdbSymmetry=false' -Xmx4g -cp build/classes stud.g01.runner.LabRunner solve --input resources/problems3.txt --case 10 --algorithm ida --heuristic pdb --timeout 60 --output results/no-symmetry.npz
# 关闭置换表，考察剪枝收益与随机内存访问成本。
java '-Dlab.ttPower=0' -Xmx4g -cp build/classes stud.g01.runner.LabRunner solve --input resources/problems3.txt --case 10 --algorithm ida --heuristic pdb --timeout 60 --output results/no-tt.npz
```

大表未必更快。开发中更大的 7+7+1 分组与更大的置换表没有改善指定难例的整体时间，默认保留 6+6+3 和 2²¹ 槽。此调优观察不替代正式、同环境重复测量。

## 后续报告可以据此展开的工作

1. 用课程要求整理理论、建模、实现和实验章节。
2. 从 CSV 生成同条件对比表，说明时间口径、失败记录、样本与重复次数。
3. 区分八数码完整状态验证、十五数码有限样本验证和理论论证的适用范围。
4. 添加 SFML 界面截图和解路径协议说明，核对棋盘移动与 Java 输出一致。
5. 若更换硬件、JDK、参数或源码，重新测量，不沿用旧数字。
