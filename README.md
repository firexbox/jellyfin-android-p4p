## 关于此 Fork

本项目 Fork 自 [jellyfin/jellyfin-android](https://github.com/jellyfin/jellyfin-android)，增加了 **IP4P 地址支持**，允许通过 [natmap](https://github.com/heiher/natmap) 的 NAT 穿透方案直连 Jellyfin 服务器，无需公网 IP 或端口转发。

### 主要更改

| 文件 | 更改说明 |
|------|---------|
| `utils/Ip4pParser.kt` | **新增** — IP4P 地址检测与解码（`2001::port:hi16:lo16` → `http://ip:port`） |
| `utils/Ip4pResolver.kt` | **新增** — DNS AAAA 记录解析，支持域名 → IP4P 地址的自动发现 |
| `ui/.../ServerSelection.kt` | IP4P 模式开关，开启后跳过服务器探测，直接解码连接。输入时实时预览解码结果 |
| `ui/.../ServerSuggestion.kt` | 已存 IP4P 服务器显示 `[IP4P]` 标签 |
| `ui/.../ConnectScreen.kt` | 传递 IP4P 标志到 ViewModel |
| `data/entity/ServerEntity.kt` | 新增 `isIp4p` 字段，持久化 IP4P 服务器类型 |
| `data/JellyfinDatabase.kt` | 数据库版本 5→6，自动迁移 |
| `data/dao/ServerDao.kt` | `insert()` 支持 `isIp4p` 参数 |
| `app/ApiClientController.kt` | 存储原始域名，每次重连时重新 DNS 解析（适配 NAT 映射变化） |
| `webapp/WebViewFragment.kt` | IP4P 域名异步解析后加载；连接失败时自动重新解析并重试一次 |
| `MainViewModel.kt` | `switchServer()` 增加 `isIp4p` 参数 |
| `res/values/strings.xml` | 新增 IP4P 相关字符串资源 |

### IP4P 地址格式

```
2001::{port}:{ipv4-hi16}:{ipv4-lo16}

示例: 2001::1f90:cb00:7101
  → IPv4: 203.0.113.1
  → 端口: 8080
  → URL:  http://203.0.113.1:8080
```

### 使用方法

1. 在服务器端部署 [natmap](https://github.com/heiher/natmap)，配置 DNS 更新脚本
2. 在 App 的服务器地址栏输入域名（或原始 IP4P 地址）
3. 打开 **IP4P address** 开关
4. 点击 **Connect** — App 通过 DNS AAAA 解析域名，解码 IP4P 地址，直连服务器
5. 服务器列表中会显示 `[IP4P]` 标签，重连时自动重新 DNS 解析

### 前置条件

- 服务器端运行 [natmap](https://github.com/heiher/natmap)
- 所有 NAT 层必须为全锥形（NAT-1）
- DNS AAAA 记录已更新为 IP4P 地址（或手动输入原始 IP4P 地址）

---

## IP2P 支持

本项目同时支持 **IP2P**（Dual A-Record Port Resolution），一种更简洁的 NAT 穿透地址方案。IP2P 将端口号编码在独立的 DNS A 记录中，客户端解析端口后以域名直连，**SSL 证书正常校验**。

### 主要更改

| 文件 | 更改说明 |
|------|---------|
| `utils/Ip2pCodec.kt` | **新增** — 端口编解码（`198.51.{hi}.{lo}` ↔ port） |
| `utils/Ip2pResolver.kt` | **新增** — DNS A 记录端口解析，label-replacement 命名约定 |
| `utils/Ip2pDns.kt` | **新增** — OkHttp DNS 覆盖（域名 → 真实 IP 映射） |
| `utils/Ip2pResult.kt` | **新增** — IP2P 解析结果密封类 |
| `ui/.../ServerSelection.kt` | IP2P 模式开关，与 IP4P 互斥。开启后仅解析端口，域名原样使用 |
| `data/entity/ServerEntity.kt` | 新增 `isIp2p` 字段 |
| `data/JellyfinDatabase.kt` | 数据库版本 6→7，自动迁移 |
| `app/ApiClientController.kt` | IP2P 域名解析与 API 客户端配置 |
| `webapp/WebViewFragment.kt` | IP2P 域名端口解析后加载；连接失败时自动重新解析 |
| `webapp/JellyfinWebViewClient.kt` | IP2P 域名直连，SSL 证书正常校验（无需绕过） |
| `res/values/strings.xml` | 新增 IP2P 相关字符串资源 |

### IP2P DNS 配置

```
sub.example.com                 A → 203.0.113.42       (服务器 IP)
sub-jellyfin.example.com        A → 198.51.31.160     (端口 8096 编码)
```

**端口编码规则**：`198.51.{port>>8}.{port&0xFF}` → `8096` ↔ `198.51.31.160`

**命名约定**：客户端从 `{hostname}` 推导端口域名为 `{first}-jellyfin.{rest}`，例：
- 输入 `sub.example.com` → 解析 `sub-jellyfin.example.com` 获取端口

### 使用方法

1. 在服务器端部署 [natmap](https://github.com/heiher/natmap)，配置 DNS 更新脚本写入两条 A 记录
2. 在 App 的服务器地址栏输入域名（如 `sub.example.com`）
3. 打开 **IP2P address** 开关
4. 点击 **Connect** — App 解析端口 A 记录，拼接域名 URL 直连服务器
5. 端口解析失败时自动 fallback 到 Jellyfin 默认端口（8096/8920）

### IP2P vs IP4P

| 维度 | IP4P | IP2P |
|------|------|------|
| DNS 记录 | 1 条 AAAA（IPv6 编码） | 2 条 A 记录（IPv4 编码） |
| URL | `http://ip:port` | `http://domain:port` |
| SSL 证书 | 需绕过校验 | ✅ 正常校验 |
| 端口来源 | 编码在 IPv6 地址中 | 独立 A 记录 |
| 移动网络 | 需 NAT-1 | 需 NAT-1（同 IP4P） |

## 开源协议

本项目基于 [Jellyfin for Android](https://github.com/jellyfin/jellyfin-android)，沿用其 [GPL-2.0](LICENSE.md) 开源协议。新增代码同样以 GPL-2.0 协议发布。
