# SnapMuse

SnapMuse 是一个基于 Java 17 + Spring Boot 的本地截图 AI 问答工具。

它支持：

- 全局鼠标长按 2 秒选点截图
- 本地网页查看对话记录和状态灯
- 配置 3 组 OpenAI 协议兼容 API，自动兜底切换
- 自定义系统提示词、截图保存目录、上下滚动快捷键

## 环境要求

- Java 17
- Maven 3.9+
- macOS 下建议使用 IntelliJ IDEA 或终端运行

## 启动

```bash
mvn spring-boot:run
```

启动后访问：

```text
http://127.0.0.1:8080
```

首次启动会自动生成运行目录：

```text
snapmuse-data/
```

其中会保存：

- `config.json`：本地配置
- `history.json`：对话记录
- `screenshots/`：截图文件

## macOS 权限

如果需要全局监听鼠标和桌面截图，请给运行宿主开启以下权限：

- 辅助功能
- 输入监控
- 屏幕录制

如果你是通过 IntelliJ IDEA 启动，请给 `IntelliJ IDEA` 开权限；如果是终端启动，请给 `Terminal` 或 `iTerm` 开权限。

## 使用方式

1. 打开网页，进入设置页配置 API、模型和系统提示词
2. 回到主页
3. 直接长按鼠标左键 2 秒记录第一个点
4. 再长按鼠标左键 2 秒记录第二个点
5. 截图会自动发送给 AI，回答会显示在本地网页中

如果第一个点后 5 秒内没有选第二个点，首个点会自动清除。

## 说明

- 运行数据目录 `snapmuse-data/` 已加入 `.gitignore`
- 仓库默认不包含你的本地 API Key、截图和历史记录
