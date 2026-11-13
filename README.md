# Java 交易实验台

单交易对（默认 `DEMO`）限价交易演示。Java 21 和 Gradle 9.9.1，后端使用 Spring Boot 4.1.2，页面使用 React、TypeScript 和 Vite。

- `engine-core`：纯 Java 撮合、IOC、改单、冻结和结算、CRC32 日志、重放、逐笔对账、负载生成和运营组件。
- `app`：REST、幂等、限流、有界队列、撤单优先、单线程撮合和组提交。HTTP 请求在日志落盘后得到结果；行情从已提交的只读快照获取。
- `frontend`：交易看板、账本与对账、运营台，每秒轮询。
- `docs/验收记录.md`：本机实测、与原版的差异和未验证项。

## 运行

随交付提供的 JAR 已包含前端，用 JDK 21 启动即可：

```sh
java -jar trading-system.jar --server.address=127.0.0.1
```

访问 <http://localhost:8080>。默认日志位于启动目录下的 `data/journal.log`。首次启动给 A、B、C、D 和 S01–S20 发放演示余额。要另开一套数据，使用 `--trading.journal-path=data/another.log`。管理操作可通过 `TRADING_ADMIN_TOKEN` 设置口令，在浏览器顶部「管理口令」输入。

可试一次部分成交：A 以 100.00 卖 5 股，再用 B 以 100.00 买 3 股，最后用 A 撤掉剩余 2 股。账本页可运行对账。REST 价格用整数分，例如 100.00 写成 10000。

```sh
curl http://localhost:8080/actuator/health
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"A","side":"SELL","price":10000,"quantity":5,"clientOrderId":"demo-1"}'
curl -X POST http://localhost:8080/api/recon/run
```

## 从源码构建

需要 JDK 21，前端建议 Node.js 24、npm 11。Windows 使用 `gradlew.bat`。

```sh
./gradlew build
./gradlew :app:bootRun
```

另开一个终端，从仓库根目录启动开发页面：

```sh
cd frontend
npm ci
npm run dev -- --port 5173
```

访问 <http://localhost:5173>，Vite 代理后端请求。要重新生成带页面的 JAR，macOS/Linux 下运行 `./scripts/build-bundle.sh`；或运行 `npm run build` 后把 `frontend/dist/` 的内容复制到 `app/src/main/resources/static/`，再运行 `./gradlew :app:bootJar`。产物在 `app/build/libs/app-0.1.0-SNAPSHOT.jar`。

```sh
./gradlew :engine-core:regimeBench
./gradlew :engine-core:journalBench
./gradlew :app:pipelineBench -PpipelineCommands=4096
python3 scripts/crash-recovery.py
```

报告写到对应模块的 `build/bench/`。`docs/portfolio/` 保存同口径基准及 SIGKILL 恢复演练的实测输出。核心落盘策略可用 `--trading.journal-fsync=EVERY_RECORD` 或 `OS_DEFAULT` 切换，默认 `GROUP`。默认每批最多 256 条、队列 2000、接入等待 2000 ms。

## 容器

```sh
docker build -t trading-system .
docker volume create trading-data
docker run --rm -p 127.0.0.1:8080:8080 -v trading-data:/data trading-system
```

容器构建文件包含前端和 Java 后端，运行用户为 10001。HTTP 外部压测脚本：`k6 run scripts/loadtest/orders.js`。本机未运行 Docker 或 k6。

## 复刻边界

实现依据是用户提供的四份 prompt。原版源码、规则 ADR 和指纹序列化未提供，故此处的生成器和指纹代码为独立实现。8 个本地回归指纹用于锁定当前版本行为；不能用来替代原版的 8 个 SHA-256。IOC 和改单按原版所述只提供核心 Java 接口。页面按文字需求重建，原版视觉参考未提供。
