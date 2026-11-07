# 节气历 jieqi

一个零依赖的二十四节气 CLI：查询节气、查看三候习俗、导出 ICS 日历。

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> 源码仓库：<https://github.com/guoguoguo-dev/jieqi>

## 特性

- 终端查询某年 24 节气
- 查看三候与传统习俗
- 查询下一个节气倒计时
- 一键导出 ICS，导入手机日历
- 导出 Markdown 表格
- 数据文件简单，欢迎 PR 扩充年份

## 环境要求

- JDK 17 或更高版本（项目使用 record 与文本块）
- 可选：Maven 3.8+，仅打包时需要

## 快速开始

```bash
mvn package
java -jar target/jieqi-0.1.0.jar list 2026
```

不装 Maven 时可以直接用 `javac`：

```bash
javac -encoding UTF-8 -d out src/main/java/io/github/guoguoguodev/jieqi/JieqiCli.java
```

把数据文件放进 classpath 后即可运行（Windows 用 `xcopy /s /y src\main\resources\* out\`）：

```bash
cp -r src/main/resources/* out/
java -cp out io.github.guoguoguodev.jieqi.JieqiCli list 2026
```

## 命令一览

| 命令 | 说明 |
|---|---|
| `jieqi list <年份>` | 列出该年全部节气 |
| `jieqi next` | 显示距离下一个节气的天数 |
| `jieqi on <yyyy-MM-dd>` | 查询某天是否为节气日，不是则给出前后节气 |
| `jieqi export ics <年份> [文件]` | 导出 ICS 日历；省略文件名则打印到标准输出 |
| `jieqi export md <年份> [文件]` | 导出 Markdown 表格 |
| `jieqi help` | 显示帮助 |
| `jieqi version` | 显示版本号 |

### 输出示例

以下示例把 `java -jar target/jieqi-0.1.0.jar` 简写为 `jieqi`。

```text
$ jieqi list 2026
二十四节气 · 2026（24 个）
----------------------------------------
2026-01-05 周一   1. 小寒  三候：雁北乡、鹊始巢、雉始雊  习俗：吃腊八粥
2026-01-20 周二   2. 大寒  三候：鸡始乳、征鸟厉疾、水泽腹坚  习俗：尾牙祭
（此处省略其余 22 条）

$ jieqi next
距离下一个节气还有 14 天
2026-11-22 周日  22. 小雪  三候：虹藏不见、天气上升地气下降、闭塞而成冬  习俗：腌腊肉

$ jieqi on 2026-11-08
2026-11-08 不是节气日
上一个：2026-11-07 立冬
下一个：2026-11-22 小雪
```

## 数据格式

数据位于 `src/main/resources/terms.csv`，每行一条，字段以 `|` 分隔，固定 6 个：

```text
年份|序号|节气|MM-dd|三候|习俗
2026|1|小寒|01-05|雁北乡;鹊始巢;雉始雊|吃腊八粥
```

- `序号`：1—24，按节气先后排列
- `MM-dd`：必须零填充，例如 `01-05`；写成 `1-5` 会报错
- `三候`：三候之间用 `;` 分隔，展示时替换为顿号
- `习俗`：单个节气的习俗描述
- 以 `#` 开头的行是注释，空行忽略

加载时会校验字段数量、序号范围与日期格式；序号重复、日期乱序、某年不足 24 条会打印警告但继续运行。

## 数据来源与校对

节气日期按紫金山天文台公布的交节时刻（北京时间）校对。收录年份：**2026**。

需要特别注意的边界日期：

- 立冬 2026-11-07 17:52
- 冬至 **2026-12-22** 04:50（不是 12-21，交节时刻已过零点，日期顺延一天）

## 退出码

| 退出码 | 含义 |
|---|---|
| 0 | 成功 |
| 1 | 数据文件缺失/格式错误、文件读写失败 |
| 2 | 命令或参数用法错误 |

## 目录结构

```text
jieqi/
├── pom.xml
├── README.md
├── LICENSE
└── src/main/
    ├── java/io/github/guoguoguodev/jieqi/JieqiCli.java
    └── resources/terms.csv
```

## Roadmap

- [x] 收录 2026 年 24 节气数据
- [ ] 内置 2020—2035 年数据
- [ ] 支持按季节/物候搜索
- [ ] GitHub Action 每日输出“今日节气”
- [ ] 精确到分钟的天文算法

## License

[MIT](LICENSE)
