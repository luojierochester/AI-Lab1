# 课程演示案例

在 SFML 播放器中点击“打开路径文件”，选择本目录的 `.npz` 文件即可播放。文件名包含数码类型、课程题号和解路径步数，例如 `15puzzle-case04-52steps.npz` 表示课程第 4 题的 52 步路径。

这里整理了[课程原始题目](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/resources/problems3.txt)的全部 10 个实例：3 个八数码、7 个十五数码。题号按原文件行号从 1 开始。每份路径均直接复制自已有求解输出，未重新生成或改动路径内容；来源链接保留在下表中。

| 演示文件 | 课程题号 | 棋盘 | 步数 | 原始求解输出 |
| --- | --- | --- | ---: | --- |
| [8puzzle-case01-21steps.npz](8puzzle-case01-21steps.npz) | 第 1 题 | 3 × 3 | 21 | [results/course-eight.csv-paths/case1-astar-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-eight.csv-paths/case1-astar-pdb-heap-r1.npz) |
| [8puzzle-case02-31steps.npz](8puzzle-case02-31steps.npz) | 第 2 题 | 3 × 3 | 31 | [results/course-eight.csv-paths/case2-astar-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-eight.csv-paths/case2-astar-pdb-heap-r1.npz) |
| [8puzzle-case03-31steps.npz](8puzzle-case03-31steps.npz) | 第 3 题 | 3 × 3 | 31 | [results/course-eight.csv-paths/case3-astar-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-eight.csv-paths/case3-astar-pdb-heap-r1.npz) |
| [15puzzle-case04-52steps.npz](15puzzle-case04-52steps.npz) | 第 4 题 | 4 × 4 | 52 | [results/course-fifteen-pdb.csv-paths/case4-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case4-ida-pdb-heap-r1.npz) |
| [15puzzle-case05-51steps.npz](15puzzle-case05-51steps.npz) | 第 5 题 | 4 × 4 | 51 | [results/course-fifteen-pdb.csv-paths/case5-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case5-ida-pdb-heap-r1.npz) |
| [15puzzle-case06-56steps.npz](15puzzle-case06-56steps.npz) | 第 6 题 | 4 × 4 | 56 | [results/course-fifteen-pdb.csv-paths/case6-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case6-ida-pdb-heap-r1.npz) |
| [15puzzle-case07-57steps.npz](15puzzle-case07-57steps.npz) | 第 7 题 | 4 × 4 | 57 | [results/course-fifteen-pdb.csv-paths/case7-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case7-ida-pdb-heap-r1.npz) |
| [15puzzle-case08-50steps.npz](15puzzle-case08-50steps.npz) | 第 8 题 | 4 × 4 | 50 | [results/course-fifteen-pdb.csv-paths/case8-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case8-ida-pdb-heap-r1.npz) |
| [15puzzle-case09-61steps.npz](15puzzle-case09-61steps.npz) | 第 9 题 | 4 × 4 | 61 | [results/course-fifteen-pdb.csv-paths/case9-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case9-ida-pdb-heap-r1.npz) |
| [15puzzle-case10-70steps.npz](15puzzle-case10-70steps.npz) | 第 10 题 | 4 × 4 | 70 | [results/course-fifteen-pdb.csv-paths/case10-ida-pdb-heap-r1.npz](https://github.com/luojierochester/AI-Lab1/blob/fdbdc1fc026625b0e0789ea9745835afb6a81494/results/course-fifteen-pdb.csv-paths/case10-ida-pdb-heap-r1.npz) |

八数码路径采用 A* + PDB，十五数码路径采用 IDA* + PDB，均取对应实验的第 1 次记录。整理时已逐一核对初态、题目目标、步数及每一步相邻交换。播放动画的时长不代表求解耗时，性能数据请查看原始实验记录。

`demo.npz` 是内置的四步操作示例，用于熟悉播放与单步按钮，不属于上述 10 道课程题，也不计入实验结果。
