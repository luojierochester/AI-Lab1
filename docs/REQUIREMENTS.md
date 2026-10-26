# 实验要求与实现位置

本清单依据课程提供的《NPuzzle求解的要求》《项目一讲解》《分工建议》和研究报告目录整理。原始教学文档保留在本地项目上级目录；本仓库提交实现和可复现证据，未撰写实验研究报告。

| 课程要求 | 实现与证据入口 |
|---|---|
| 使用课程 Java 框架，不改已有核心实现 | `src/core/` 原样保留；`verification/framework-baseline.json` 锁定 26 个原始 Java 文件；测试检查 SHA-256 |
| 第一阶段：A* + 优先队列，两个指定八数码在 1 秒内求解 | `PuzzleFeeder`、`PuzzleBoard`、`NPuzzleProblem`、`PqFrontier` 接入原 `BestFirstSearcher`；性能以 `results/` 实测数据为准 |
| 第二阶段：十五数码 IDA* | `stud/g01/solver/IdAStar.java`；`resources/problems.txt` 第 4 行和 `problems3.txt` 的四阶实例 |
| 第三阶段：不相交模式数据库，指定难例 1 分钟内求解 | `PuzzleHeuristics`、`PatternDatabase`；`resources/problems3.txt` 第 10 行；同时记录准备、搜索和总耗时 |
| 第四阶段：解路径可视化 | `visualizer/` 中的 C++ SFML 程序，读取 Java 导出的 NPZ1 文本文件 |
| 三种启发函数与 A*/IDA* 比较 | CLI `benchmark`，原始 CSV、重复次数、机器信息及脚本 |
| Open 表数据结构比较 | 同一 A*、启发函数和完整排序规则下比较课程 `ListFrontier` 与索引堆 |
| 正确性与最优性 | 完整八数码 BFS 距离表、可采纳性与一致性检查、独立路径校验、任意目标测试 |
| 队友继续撰写报告 | `docs/EXPERIMENTS.md` 提供指标定义和局限，`docs/ALGORITHMS.md` 解释实现；不代填姓名、学号或虚构成绩 |

## 任务边界

- 课程建议 Zobrist 哈希。本实现对 2/3/4 阶棋盘采用 64 位精确编码：每个格子 4 位，状态本身不发生哈希碰撞。置换表的槽位仍通过散列计算，但命中时比较完整状态键。
- 可视化不需要与求解器实时通信：先导出路径，再由 SFML 播放，符合分工建议。
- PDB 是预计算的启发式查找表，不需要数据库服务器。
- 文档未明确一分钟是否包含预处理，因此同时提供冷启动总耗时和已缓存搜索耗时；不将两者混用。
- 分工建议提及空位距离、双向 BFS 等拓展。它们不替代正式要求的三种核心启发函数。本实现提供可测量的对称估值与增量更新优化。
- 截止时间、提交命名、组队人数和学校平台要求仍以教师通知为准。
