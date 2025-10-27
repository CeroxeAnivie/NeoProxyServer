

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
- 🌍 **IP 地理位置查询**（可选），显示客户端来源地区
- 🖥️ **Windows Terminal / Linux / macOS 全兼容**
- 🌐 **网页内容拦截**，可自定义拦截消息
- 🔄 **系统代理支持**，自动检测并使用系统代理进行地理位置查询
- 📊 **实时流量监控**，定期通知剩余流量

---

## 🚀 快速开始

### 环境要求

- Java 21+
- Maven（用于构建）
- 必须：需要配合 [NeoLink](https://github.com/NeoLinkProxy/NeoLink) 客户端使用
- 必须（除非本地测试）：公网 IP 或域名（用于外网访问）

### 构建项目

```bash
git clone https://github.com/CeroxeAnivie/PlethoraAPI
mvn install

git clone https://github.com/CeroxeAnivie/NeoProxyServer.git
cd NeoProxyServer
mvn clean package
```

生成的可执行 JAR 文件位于 `target/NeoProxyServer-XXXX.jar`。

### 启动服务端

```bash
java -jar target/NeoProxyServer-XXXX.jar
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
CUSTOM_BLOCKING_MESSAGE=如有疑问，请联系您的系统管理员。  # 自定义网页拦截消息
```

> 修改后重启生效。

---

## 🔑 密钥管理（控制台命令）

启动后，可通过控制台输入命令管理授权密钥：

| 命令                                                    | 说明                                                        |
|-------------------------------------------------------|-----------------------------------------------------------|
| `key add <name> <balance> <expireTime> <port> <rate>` | 创建新密钥<br>例：`key add demo 1000 "2025/12/31-23:59" 8080 10` |
| `key set demo p=3344-3350`                            | 修改端口策略为动态范围                                               |
| `key enable demo` / `key disable demo`                | 启用或禁用密钥                                                   |
| `key list`                                            | 表格化列出所有密钥（含客户端连接数）                                        |
| `key lp demo`                                         | 查看单个密钥详情                                                  |
| `alert enable` / `alert disable`                      | 开启/关闭详细日志                                                 |

> 时间格式：`yyyy/MM/dd-HH:mm`  
> 端口支持：`8080`（固定）或 `3344-3350`（动态范围）

---

## 🛑 IP 管理（控制台命令）

服务端支持通过控制台命令封禁或解封特定 IP 地址，以增强安全性。

| 命令                   | 说明                             |
|----------------------|--------------------------------|
| `ban <ip_address>`   | 封禁指定的 IP 地址。被封禁的 IP 将无法连接到服务端。 |
| `unban <ip_address>` | 解封指定的 IP 地址。                   |
| `listbans`           | 列出所有当前被封禁的 IP 地址。              |

---

## 🌐 网页内容管理

### 网页拦截功能

NeoProxyServer 支持拦截网页内容，当密钥未启用网页访问权限时，系统会自动拦截并显示自定义的拦截页面。

| 命令                          | 说明                         |
|-----------------------------|----------------------------|
| `web enable <key>`          | 为指定密钥启用网页访问权限           |
| `web disable <key>`         | 为指定密钥禁用网页访问权限           |

拦截页面模板位于 `/templates/forbidden.html`，可通过修改 `config.cfg` 中的 `CUSTOM_BLOCKING_MESSAGE` 来自定义拦截消息。

---

## 🌍 IP 地理位置查询

NeoProxyServer 自动查询客户端 IP 的地理位置信息，并在控制台显示。该功能支持：

- 自动检测系统代理设置
- 使用多个地理位置 API 服务（ip.sb、ip-api.com、ipapi.co）
- 竞速查询机制，优先使用响应最快的 API
- 本地/私有 IP 地址的自动识别

地理位置信息会在客户端连接时显示，并可通过 `list` 命令查看所有活跃客户端的地理位置信息。

---

## 📊 流量控制

NeoProxyServer 提供精细的流量控制功能：

- **流量配额**：每个密钥可设置 MiB 级别的流量配额
- **速率限制**：支持 Mbps 级别的速率限制
- **实时监控**：每消耗一定流量（默认 10MB）会通知客户端剩余流量
- **自动禁用**：流量耗尽时自动禁用密钥

---

## 🎛️ 端口分配

NeoProxyServer 支持两种端口分配模式：

- **固定端口**：为密钥分配一个固定的外部端口
- **动态端口范围**：为密钥分配一个端口范围，系统自动选择可用端口

动态端口范围示例：`3344-3350`，系统会在此范围内自动选择可用端口分配给客户端。

---

## 📝 日志记录

NeoProxyServer 提供详细的日志记录功能：

- **连接日志**：记录所有客户端连接和断开事件
- **错误日志**：记录系统错误和异常
- **调试日志**：通过 `--debug` 参数启用详细调试信息
- **日志文件**：自动保存到 `logs/` 目录

---

## 🔒 安全性

NeoProxyServer 提供多层安全保护：

- **AES 加密**：所有数据传输均使用 AES 加密
- **IP 封禁**：支持手动或自动封禁恶意 IP
- **密钥管理**：支持密钥有效期和权限控制
- **网页拦截**：可拦截未授权的网页访问
- **速率限制**：防止流量滥用

---

## 🛠️ 故障排除

### 常见问题

1. **如何让 IP 查询 API 连接走代理**
    - 如果是 Windows ，使用 Clash for Windows，开启系统代理，<br>使用 JVM 参数 `-Djava.net.useSystemProxies=true` 即可自动识别，比如
    - ```
      java -Djava.net.useSystemProxies=true -jar NeoProxyServer-4.9.2.jar
      ```
    - 如果是 Linux，可以参考 [这里](https://gitee.com/tools-repo/clash-for-linux-install#%E5%BF%AB%E9%80%9F%E5%BC%80%E5%A7%8B) 安装 Clash for Linux ，配置好后开启代理。
    - 配置完以后执行 env | grep -i proxy ，你应该看到类似以下环境变量的输出
    - >root@serXXXX:~# env | grep -i proxy
      no_proxy=localhost,127.0.0.1,::1
      https_proxy=http://127.0.0.1:7890
      NO_PROXY=localhost,127.0.0.1,::1
      HTTPS_PROXY=http://127.0.0.1:7890
      HTTP_PROXY=http://127.0.0.1:7890
      http_proxy=http://127.0.0.1:7890
      ALL_PROXY=socks5h://127.0.0.1:7890
      all_proxy=socks5h://127.0.0.1:7890
    - 关注 HTTP_PROXY 和 HTTPS_PROXY 的值，使用以下 bash 文件来启动 jar 即可<br>会自动识别环境变量
    - ```
      export http_proxy="http://127.0.0.1:7890"
      export https_proxy="http://127.0.0.1:7890"
      
      java -Djava.net.useSystemProxies=true -jar NeoProxyServer-4.8.2.jar
      ```

2. **端口被占用**
    - 检查配置文件中的端口设置
    - 确保端口未被其他程序占用
    - 使用 `netstat` 或 `lsof` 命令检查端口状态

3. **客户端连接失败**
    - 检查客户端版本是否兼容
    - 确认密钥是否有效且未过期
    - 检查网络防火墙设置

4. **地理位置查询失败**
    - 检查系统代理设置
    - 确认网络连接正常
    - 查看调试日志中的错误信息

5. **流量控制不生效**
    - 确认密钥已设置流量配额
    - 检查速率限制设置
    - 查看实时流量监控日志

### 调试模式

启动时添加 `--debug` 参数可启用详细调试信息：

```bash
java -jar target/NeoProxyServer-XXXX.jar --debug
```

---

## 💬 支持与反馈

如果您在使用过程中遇到任何问题或有任何建议，欢迎通过以下方式联系我们：

- 📧 **QQ 群**：304509047

- 🐛 **问题反馈**：如果您发现了 Bug 或有功能建议，请在 GitHub 上提交 Issue。

- 📖 **文档与教程**：我们会在 Wiki 中持续更新使用教程和最佳实践。

---

## 📄 许可证

本项目采用 **MIT License** 开源：

---
> 📬 **提示**：若用于生产环境，请务必修改 `LOCAL_DOMAIN_NAME` 为你的公网 IP 或域名，并配置防火墙开放对应端口。