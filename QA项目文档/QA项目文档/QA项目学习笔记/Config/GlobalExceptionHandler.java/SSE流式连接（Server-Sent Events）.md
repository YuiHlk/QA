## 一、什么是 SSE

**服务端推送事件**，一套基于标准 HTTP 1.1 的**单向长连接流式通信方案**。

核心：客户端发起一次 HTTP 请求，服务端持续保持连接，源源不断向客户端推送文本数据，客户端不用重复发请求。

### 核心定位

- 单向：只能**服务端 → 客户端**发消息，客户端无法实时回传数据
- 底层：纯 HTTP，不需要[[协议升级Upgrade]]（区别 WebSocket）
- 浏览器原生：内置 `EventSource` API，无需引入第三方库
- 传输：仅 UTF-8 文本，不支持二进制

## 二、底层原理

### 1. 依赖两个 HTTP 特性

1. **Keep-Alive**：TCP 连接复用，请求结束不立刻断开
2. **Chunked 分块传输（Transfer-Encoding: chunked）**
    
    普通接口会先计算完整数据，带上 `Content-Length` 一次性返回；
    
    SSE 不预先知道总长度，分小块实时刷给前端，服务端随时可追加数据。

### 2. 完整请求流程

1. 前端通过 `EventSource` 发起 GET 请求到后端接口
2. 后端返回固定 SSE 响应头，告知浏览器这是流，不要关闭连接
3. 后端循环生成数据，按 SSE 规范格式分段写入输出流，每次 `flush()` 强制发送
4. 前端收到分片后，解析 `data:` 内容触发 `onmessage` 回调
5. 连接断开（网络 / 页面关闭）时，`EventSource` 默认自动重试连接

## 三、强制响应头（缺一不可）

后端必须返回这组 Header，否则浏览器不会识别为 SSE：

```http
# 标识当前是SSE流，固定值
Content-Type: text/event-stream
# 禁止浏览器缓存流式数据
Cache-Control: no-cache
# 保持长连接
Connection: keep-alive
# 分块传输，无固定长度
Transfer-Encoding: chunked
```

额外可选：
```http
X-Content-Type-Options: nosniff
```

## 四、SSE 标准消息格式

每条消息由若干行键值组成，**消息与消息之间必须用空行 `\n\n` 分隔**。

支持 5 种指令：`data` / `event` / `id` / `retry` / `:`

### 1. data: 数据主体（最常用）

承载要推送的业务内容，多行 data 会自动拼接。
```plaintext
data: 第一段内容
data: 第二段拼接内容

data: {"text":"AI返回片段"}

```

空行 `\n\n` 代表本条消息结束，前端触发 `onmessage`。

### 2. event: 自定义事件名

区分不同业务消息，前端用 `addEventListener` 单独监听，不走默认 `onmessage`。
```plaintext
event: complete
data: 回答生成完毕

event: log
data: 模型推理耗时200ms

```

前端：
```js
source.addEventListener('complete', e => {})
```

### 3. id: 消息 ID

给每条消息唯一标识。

- 连接断开重连时，浏览器自动携带请求头 `Last-Event-ID: xxx`
- 后端可根据 ID 补发断开期间丢失的消息，实现断点续传
```plaintext
id: 1001
data: 内容
```

### 4. retry: 重连间隔（单位 ms）

修改浏览器断线后自动重试的等待时间，默认 3000ms。
```plaintext
retry: 1000
data: 1秒后断连自动重连
```

### 5. : 注释（冒号开头）

仅用于保活心跳，前端会直接忽略，防止长时间无数据被网关断开连接。
```plaintext
: ping
```

后端定时推送 `: ping\n\n` 维持连接。
## 五、SSE 核心特性

1. **自动断线重连**
    
    网络波动、网关超时断开后，浏览器会自动发起新请求，配合 `id` 实现断点续传。WebSocket 需要自己手动实现重连。
    
2. **断点续传（Last-Event-ID）**
    
    断连重连时请求头携带上次消息 ID，后端可补发中间丢失的数据，适合实时日志、监控。
    
3. **轻量，开销小**
    
    单 TCP 长连接持续推送，对比前端轮询（每秒发一次 HTTP 请求）极大减少连接与请求开销。
    
4. **天然兼容 HTTP 中间件**
    
    Nginx、网关、鉴权、限流、日志都能正常处理，不用额外改造协议，WebSocket 很多网关需要特殊升级配置。
    

## 六、优缺点

### 优点

1. 实现简单，浏览器原生支持，无需第三方 SDK
2. 基于标准 HTTP，网关、反向代理友好
3. 自带自动重连、消息 ID 断点续传、自定义事件
4. 服务端开发成本低，相比 WebSocket 代码量更少
5. 资源占用低，适合大量客户端单向接收数据

### 缺点

1. **单向通信**：前端不能实时发消息给后端，交互场景受限
2. 仅支持文本，无法传输二进制（文件、视频流不行）
3. 原生 EventSource 仅 GET，不能 POST、自定义请求头
4. 浏览器并发限制：同一域名最多同时打开 6 个 SSE 连接
5. 老 IE 浏览器不支持（现代浏览器全兼容）