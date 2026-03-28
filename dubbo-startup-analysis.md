# Dubbo 启动流程分析

从 `org.apache.dubbo.config.bootstrap.DubboBootstrap#start()` 开始分析

---

## 1. 启动入口 - DubboBootstrap

**文件路径**: `dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/bootstrap/DubboBootstrap.java`

### 1.1 核心方法

```java
public DubboBootstrap start() {
    this.start(true);
    return this;
}

public DubboBootstrap start(boolean wait) {
    Future future = applicationDeployer.start();
    if (wait) {
        try {
            future.get();
        } catch (Exception e) {
            throw new IllegalStateException("await dubbo application start finish failure", e);
        }
    }
    return this;
}
```

### 1.2 关键点说明

- **DubboBootstrap** 是整个 Dubbo 应用的引导类，设计为单例模式
- 实际启动逻辑委托给 **ApplicationDeployer** (默认实现是 `DefaultApplicationDeployer`)
- 通过 `startFuture.get()` 可阻塞等待启动完成

---

## 2. 应用部署器 - DefaultApplicationDeployer

**文件路径**: `dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/deploy/DefaultApplicationDeployer.java`

### 2.1 启动流程概览

```
start()
  ├─ 检查状态
  ├─ onStarting() - 触发启动事件
  ├─ initialize() - 初始化
  └─ doStart() - 启动模块
```

---

## 3. 初始化阶段 (initialize)

### 3.1 初始化步骤

```java
public void initialize() {
    // 1. 触发 onInitialize 事件
    onInitialize();

    // 2. 注册 Shutdown Hook
    registerShutdownHook();

    // 3. 启动配置中心
    startConfigCenter();

    // 4. 加载应用配置
    loadApplicationConfigs();

    // 5. 初始化模块部署器
    initModuleDeployers();

    // 6. 初始化 Metrics 报告器
    initMetricsReporter();

    // 7. 初始化 Metrics 服务
    initMetricsService();

    // 8. 启动元数据中心
    startMetadataCenter();
}
```

---

### 3.2 启动配置中心 (startConfigCenter)

**步骤说明**:

1. **加载 ApplicationConfig**: 从配置属性中加载应用配置
2. **设置模型名称**: 将应用名设置到 ApplicationModel
3. **加载 ConfigCenterConfig**: 从配置属性中加载配置中心配置
4. **兼容处理**: 如果配置中心未配置，尝试使用注册中心作为配置中心
5. **连接配置中心**: 连接远程配置中心并拉取配置
6. **更新环境配置**: 将拉取的配置更新到 Environment

**关键代码位置**: `DefaultApplicationDeployer.java:260-306`

---

### 3.3 启动元数据中心 (startMetadataCenter)

**步骤说明**:

1. **兼容处理**: 如果元数据中心未配置，尝试使用注册中心作为元数据中心
2. **验证配置**: 验证 MetadataReportConfig 配置
3. **初始化实例**: 初始化 MetadataReportInstance

**关键代码位置**: `DefaultApplicationDeployer.java:308-339`

---

### 3.4 初始化模块部署器 (initModuleDeployers)

**步骤说明**:

1. **确保默认模块创建**: 调用 `applicationModel.getDefaultModule()` 确保默认模块已创建
2. **初始化所有模块**: 遍历所有 ModuleModel，调用其 deployer 的 initialize() 方法

**关键代码位置**: `DefaultApplicationDeployer.java:247-254`

---

## 4. 启动阶段 (doStart)

### 4.1 启动流程

```java
private void doStart() {
    // 启动所有模块
    startModules();
}
```

**关键代码位置**: `DefaultApplicationDeployer.java:722-750`

---

### 4.2 启动模块 (startModules)

```java
private void startModules() {
    // 1. 先启动内部模块
    prepareInternalModule();

    // 2. 启动所有待处理的模块
    for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
        if (moduleModel.getDeployer().isPending()) {
            moduleModel.getDeployer().start();
        }
    }
}
```

**关键代码位置**: `DefaultApplicationDeployer.java:752-762`

---

## 5. 模块部署器 - DefaultModuleDeployer

**文件路径**: `dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/deploy/DefaultModuleDeployer.java`

### 5.1 模块启动流程

```
start()
  ├─ applicationDeployer.initialize() - 确保应用已初始化
  └─ startSync() - 同步启动
       ├─ onModuleStarting() - 触发模块启动事件
       ├─ initialize() - 模块初始化
       ├─ exportServices() - 导出服务
       ├─ prepareInternalModule() - 准备内部模块
       ├─ referServices() - 引用服务
       ├─ (可选) 等待异步导出/引用完成
       ├─ onModuleStarted() - 触发模块启动完成事件
       ├─ registerServices() - 注册服务到注册中心
       ├─ checkReferences() - 检查引用
       └─ completeStartFuture() - 完成启动 Future
```

---

### 5.2 模块初始化 (initialize)

```java
public void initialize() {
    // 1. 触发 onInitialize 事件
    onInitialize();

    // 2. 加载模块配置
    loadConfigs();

    // 3. 读取 ModuleConfig，获取异步导出/引用配置
    //    - exportAsync
    //    - referAsync
    //    - background
}
```

**关键代码位置**: `DefaultModuleDeployer.java:116-149`

---

### 5.3 导出服务 (exportServices)

**步骤说明**:

1. 遍历所有 ServiceConfig
2. 调用 `exportServiceInternal()` 导出每个服务
3. 支持同步和异步两种导出方式

```java
private void exportServiceInternal(ServiceConfigBase sc) {
    // 1. 刷新配置
    if (!serviceConfig.isRefreshed()) {
        serviceConfig.refresh();
    }

    // 2. 检查是否已导出
    if (sc.isExported()) {
        return;
    }

    // 3. 异步导出或同步导出
    if (exportAsync || sc.shouldExportAsync()) {
        // 异步导出
        CompletableFuture.runAsync(() -> {
            sc.export();
            exportedServices.add(sc);
        }, executor);
        asyncExportingFutures.add(future);
    } else {
        // 同步导出
        sc.export(RegisterTypeEnum.AUTO_REGISTER_BY_DEPLOYER);
        exportedServices.add(sc);
    }
}
```

**关键代码位置**: `DefaultModuleDeployer.java:422-481`

---

### 5.4 引用服务 (referServices)

**步骤说明**:

1. 遍历所有 ReferenceConfig
2. 调用 `referenceCache.get()` 引用每个服务
3. 支持同步和异步两种引用方式

```java
private void referServices() {
    configManager.getReferences().forEach(rc -> {
        // 1. 刷新配置
        if (!referenceConfig.isRefreshed()) {
            referenceConfig.refresh();
        }

        // 2. 是否需要初始化
        if (rc.shouldInit()) {
            // 3. 异步引用或同步引用
            if (referAsync || rc.shouldReferAsync()) {
                CompletableFuture.runAsync(() -> {
                    referenceCache.get(rc, false);
                }, executor);
                asyncReferringFutures.add(future);
            } else {
                referenceCache.get(rc, false);
            }
        }
    });
}
```

**关键代码位置**: `DefaultModuleDeployer.java:516-559`

---

### 5.5 注册服务 (registerServices)

**步骤说明**:

1. 遍历所有 ServiceConfig
2. 如果 `isRegister()` 不为 false，则调用 `registerServiceInternal()` 注册服务
3. 刷新服务实例

```java
private void registerServices() {
    for (ServiceConfigBase sc : configManager.getServices()) {
        if (!Boolean.FALSE.equals(sc.isRegister())) {
            registerServiceInternal(sc);
        }
    }
    applicationDeployer.refreshServiceInstance();
}
```

**关键代码位置**: `DefaultModuleDeployer.java:428-435`

---

### 5.6 检查引用 (checkReferences)

**步骤说明**:

1. 获取检查超时时间 (默认 30 秒)
2. 遍历所有 ReferenceConfig
3. 调用 `referenceCache.check()` 检查引用是否可用

**关键代码位置**: `DefaultModuleDeployer.java:437-443`

---

## 6. 应用实例准备 (prepareApplicationInstance)

当模块启动完成后，会触发 `prepareApplicationInstance()`:

```java
public void prepareApplicationInstance() {
    // 1. 导出 MetricsService
    exportMetricsService();

    // 2. 如果是消费者实例也需要注册
    if (isRegisterConsumerInstance()) {
        // 3. 导出元数据服务
        exportMetadataService();

        // 4. 注册服务实例
        if (hasPreparedApplicationInstance.compareAndSet(false, true)) {
            registerServiceInstance();
        }
    }
}
```

**关键代码位置**: `DefaultApplicationDeployer.java:765-780`

---

### 6.1 注册服务实例 (registerServiceInstance)

**步骤说明**:

1. 注册元数据和服务实例
2. 启动定时任务，定期刷新元数据和服务实例

```java
private void registerServiceInstance() {
    // 1. 注册服务实例
    ServiceInstanceMetadataUtils.registerMetadataAndInstance(applicationModel);

    // 2. 启动定时任务刷新元数据
    asyncMetadataFuture = frameworkExecutorRepository
        .getSharedScheduledExecutor()
        .scheduleWithFixedDelay(
            () -> {
                ServiceInstanceMetadataUtils.refreshMetadataAndInstance(applicationModel);
            },
            0,
            publishDelay,
            TimeUnit.MILLISECONDS);
}
```

**关键代码位置**: `DefaultApplicationDeployer.java:965-1022`

---

## 7. 状态检查与事件通知

### 7.1 状态流转

```
PENDING
  ↓
STARTING
  ↓
STARTED ← (所有模块启动完成)
  ↓
STOPPING
  ↓
STOPPED

或

FAILED (任一模块启动失败)
```

### 7.2 模块状态变化通知

当模块状态变化时，会调用 `notifyModuleChanged()`，进而触发 `checkState()` 计算并更新应用状态。

**关键代码位置**: `DefaultApplicationDeployer.java:1146-1194`

---

## 8. 整体架构图

```
DubboBootstrap
    │
    └─► ApplicationDeployer (DefaultApplicationDeployer)
          │
          ├─ initialize()
          │   ├─ startConfigCenter()
          │   ├─ loadApplicationConfigs()
          │   ├─ initModuleDeployers()
          │   ├─ initMetricsReporter()
          │   ├─ initMetricsService()
          │   └─ startMetadataCenter()
          │
          └─ doStart()
              └─ startModules()
                  └─► ModuleDeployer (DefaultModuleDeployer) [for each module]
                        │
                        ├─ initialize()
                        │   └─ loadConfigs()
                        │
                        └─ startSync()
                            ├─ exportServices()
                            │   └─ ServiceConfig.export()
                            ├─ referServices()
                            │   └─ ReferenceConfig.get()
                            ├─ registerServices()
                            └─ checkReferences()
```

---

## 9. 关键类总结

| 类名 | 职责 | 文件路径 |
|------|------|----------|
| DubboBootstrap | 应用启动入口，单例模式 | dubbo-config-api/.../DubboBootstrap.java |
| DefaultApplicationDeployer | 应用级部署器，负责应用初始化和启动 | dubbo-config-api/.../DefaultApplicationDeployer.java |
| DefaultModuleDeployer | 模块级部署器，负责服务导出和引用 | dubbo-config-api/.../DefaultModuleDeployer.java |
| ApplicationModel | 应用级作用域模型 | dubbo-rpc-api/.../ApplicationModel.java |
| ModuleModel | 模块级作用域模型 | dubbo-rpc-api/.../ModuleModel.java |
| ConfigManager | 配置管理器 | dubbo-config-api/.../ConfigManager.java |

---

## 10. 核心扩展点

- **ApplicationDeployListener**: 应用部署监听器
- **ModuleDeployListener**: 模块部署监听器
- **DubboBootstrapStartStopListener**: DubboBootstrap 启停监听器

