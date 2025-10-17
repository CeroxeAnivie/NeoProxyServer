# 🌐 NeoProxyServer – 高性能内网穿透服务端

> 💡 **NeoProxyServer** 是一个基于 Java 21 构建的现代化、高性能、支持流量控制与动态端口分配的内网穿透服务端，适用于开发者、运维人员及个人用户快速暴露本地服务至公网。

---

## 📦 特性亮点

- ✅ **Java 21 + Maven 构建**，现代 Java 特性加持
- 🔐 **AES 加密通信**，保障数据安全
- 📊 **流量配额控制**（MiB 级别），防止滥用
- ⏳ **密钥有效期管理**，自动清理过期授权
- 🎛️ **动态端口范围分配**（如 `3344-3350`）或固定端口
- 🧠 **智能速率限制**（Mbps 级）
- 📝 **交互式控制台**，支持中文/英文双语
- 📁 **H2 嵌入式数据库**，无需额外依赖
- 🛑 **IP 封禁机制**，增强安全性
- 🖥️ **Windows Terminal / Linux / macOS 全兼容**

---

## 🚀 快速开始

### 环境要求

- Java 21+
- Maven（用于构建）
- 必须：需要配合 [NeoLink](https://github.com/NeoLinkProxy/NeoLink) 客户端使用
- 必须（除非本地测试）：公网 IP 或域名（用于外网访问）

### 构建项目

```bash
git clone <your-repo-url>
cd NeoProxyServer
mvn clean package
```

生成的可执行 JAR 文件位于 `target/NeoProxyServer-3.6-RELEASE.jar`。

### 启动服务端

```bash
java -jar target/NeoProxyServer-3.6-RELEASE.jar
```

> 默认监听：
> - **Hook 端口**：`801`（客户端注册）
> - **连接端口**：`802`（数据通道）

启动后，服务端将自动生成 `config.cfg` 配置文件和 `sk.mv.db` 数据库。

---

## 🔧 配置说明 (`config.cfg`)

```ini
LOCAL_DOMAIN_NAME=localhost          # 建议替换为你的公网IP或域名
ALERT=true                           # 是否启用详细连接日志
ENABLE_BAN=true                      # 是否启用IP封禁
SO_TIMEOUT=1000                      # 服务端等待客户端响应超时（毫秒）
TELL_BALANCE_MIB=10                  # 每消耗10MB流量通知剩余量
HOST_HOOK_PORT=801                   # 客户端注册端口
HOST_CONNECT_PORT=802                # 数据通道端口
BUFFER_LEN=4096                      # 数据缓冲区大小
SAVE_DELAY=3000                      # 密钥状态保存间隔（毫秒）
AES_KEY_SIZE=128                     # 加密密钥长度
```

> 修改后重启生效。

---

## 🔑 密钥管理（控制台命令）

启动后，可通过控制台输入命令管理授权密钥：

| 命令 | 说明 |
|------|------|
| `key add <name> <balance> <expireTime> <port> <rate>` | 创建新密钥<br>例：`key add demo 1000 "2025/12/31-23:59" 8080 10` |
| `key set demo p=3344-3350` | 修改端口策略为动态范围 |
| `key enable demo` / `key disable demo` | 启用或禁用密钥 |
| `key list` | 表格化列出所有密钥（含客户端连接数） |
| `key lp demo` | 查看单个密钥详情 |
| `alert enable` / `alert disable` | 开启/关闭详细日志 |

> 时间格式：`yyyy/MM/dd-HH:mm`  
> 端口支持：`8080`（固定）或 `3344-3350`（动态范围）

---

## 🌍 客户端连接流程

1. 客户端输入**序列号（密钥）**
2. 服务端验证版本、密钥有效性、端口可用性
3. 分配外网端口（如 `localhost:8080`）
4. 用户通过 `http://你的域名:8080` 访问内网服务

---

## 📄 许可证

本项目采用 **MIT License** 开源：

---

> 📬 **提示**：若用于生产环境，请务必修改 `LOCAL_DOMAIN_NAME` 为你的公网 IP 或域名，并配置防火墙开放对应端口。