# 夺宝奇缘 1+2 · Android 17 移植工程

一个基于 [VMRP](https://github.com/vmrp/vmrp) WebAssembly 的实验性 MRP 安卓封装：两个独立游戏章节、独立存档、第一部结局的 OCR/文字回调检测与安全章节切换。

> **验收状态：尚未完成。** 不要把任一 CI 的静态测试或测试用 APK 当成《夺宝奇缘》双部曲最终安装包。

## 真实开发进展（2026-10-27）

- ✅ 已将 Android、JS 桥接、剧情状态机、存档代码与源码测试完整上传到此公开仓库。
- ✅ GitHub Actions 的两个源码检查工作流通过。
- ✅ 公共云端作业已实际下载 VMRP 的 JS/WASM，并通过了 WASM 文件格式验证；不再是模拟占位文件。
- ✅ 已用上游 VMRP 的测试文件**而非夺宝奇缘游戏**在云端编译过一次调试 APK（stable Android SDK 36）。
- ✅ 在临时作业中已下载到与用户上传文件 SHA256 一致的两部 MRP；不会将其提交公开源码树。
- 🔄 Android 17 / API 37 的真实模拟器启动验证仍按 GitHub Actions 日志判定，可能失败，需要进一步修复。
- ❌ 还没有第一部真实通关的自动衔接验收。
- ❌ 没有经过验证的可分发最终 APK。

## 工作流

- [源码静态检查](.github/workflows/source-integrity.yml) / [Java、JS、Python 测试](.github/workflows/test-public-source.yml)：无需第三方游戏资产。
- [VMRP 运行引擎与 Android 17 环境冒烟测试](.github/workflows/runtime-smoke.yml)：使用上游测试文件，**不能证明《夺宝奇缘》可玩**。
- [两部历史 MRP 的临时真实运行验证](.github/workflows/real-games-ci.yml)：下载到临时构建机，严格校验用户提供的游戏 SHA256，执行 Android 17 启动/人工章节切换测试；不公开发布 APK、游戏文件或截图。
- [本地完整构建与模拟器脚本](.github/workflows/build-android.yml)：必须使用有权运行的 MRP 游戏文件。所生成 APK 含第三方游戏数据，不应未经授权发布。

Android 编译使用 **compileSdk 36** 是 SDK 工具兼容选择；最终应用将在 **Android 17/API 37** 系统镜像中测试，二者不是同一概念。

## 游戏文件

把自行合法持有的两部 MRP 分别放在：
- `app/src/main/assets/games/part1.mrp`
- `app/src/main/assets/games/part2.mrp`

公开仓库不包含两部游戏本体。代码通过 SHA256 校验二进制文件是否与已分析版本一致。

## 许可证

VMRP 按 GPL-3.0 发布；涉及 VMRP 二进制分发时须遵守完整许可义务。游戏画面、剧情、资源等版权归各自权利人所有。本仓库开放代码不等于取得了原版游戏的公开分发权。
