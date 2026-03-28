# Dubbo SPI 机制完整分析

## 目录
1. [SPI 机制概述](#spi-机制概述)
2. [核心 SPI 注解](#核心-spi-注解)
3. [RPC 相关 SPI](#rpc-相关-spi)
4. [注册中心相关 SPI](#注册中心相关-spi)
5. [序列化相关 SPI](#序列化相关-spi)
6. [网络传输相关 SPI](#网络传输相关-spi)
7. [集群相关 SPI](#集群相关-spi)
8. [配置中心相关 SPI](#配置中心相关-spi)
9. [元数据相关 SPI](#元数据相关-spi)
10. [监控与指标相关 SPI](#监控与指标相关-spi)
11. [过滤器相关 SPI](#过滤器相关-spi)
12. [其他通用 SPI](#其他通用-spi)

---

## SPI 机制概述

Dubbo SPI (Service Provider Interface) 是 Dubbo 框架的核心扩展机制，它基于 Java SPI 进行了增强，提供了：

- **键值对配置**：支持别名和类名的映射
- **按需加载**：延迟加载扩展实现
- **依赖注入**：扩展之间的自动依赖注入
- **包装机制**：支持 Wrapper 类对扩展进行包装
- **自适应扩展**：根据 URL 参数动态选择实现
- **激活机制**：根据条件自动激活扩展

### SPI 配置文件位置

Dubbo 在以下位置查找 SPI 配置文件：
- `META-INF/dubbo/internal/` - 内部 SPI
- `META-INF/dubbo/` - 用户自定义 SPI
- `META-INF/services/` - 兼容 Java SPI

### 配置文件格式

```
name1=com.foo.impl.Class1
name2=com.foo.impl.Class2
```

---

## 核心 SPI 注解

### @SPI

标记扩展接口，定义默认扩展和作用域。

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SPI {
    // 默认扩展名称
    String value() default "";
    // SPI 作用域
    ExtensionScope scope() default ExtensionScope.APPLICATION;
}
```

### ExtensionScope 枚举

- `FRAMEWORK` - 框架级作用域，所有应用共享
- `APPLICATION` - 应用级作用域，每个应用独立
- `MODULE` - 模块级作用域，每个模块独立

### @Adaptive

标记自适应扩展方法或类，根据 URL 参数动态选择实现。

### @Activate

标记可自动激活的扩展，支持根据 URL 参数或分组条件激活。

---

## RPC 相关 SPI

### 1. Protocol - 协议

**接口**: `org.apache.dubbo.rpc.Protocol`

**默认值**: `dubbo`

**作用域**: `FRAMEWORK`

**作用**: RPC 协议扩展，封装远程调用细节

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| dubbo | `org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol` | Dubbo 原生协议，高效二进制传输 |
| rest | `org.apache.dubbo.rpc.protocol.rest.RestProtocol` | REST 协议，基于 HTTP |
| tri | `org.apache.dubbo.rpc.protocol.tri.TripleProtocol` | Triple 协议，基于 gRPC/HTTP2 |
| injvm | `org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol` | JVM 内部协议，用于本地调用 |
| registry | `org.apache.dubbo.registry.integration.RegistryProtocol` | 注册协议，配合注册中心使用 |
| qos | `org.apache.dubbo.qos.protocol.QosProtocolWrapper` | QoS 协议包装 |

**核心方法**:
- `export(Invoker<T>)` - 导出服务
- `refer(Class<T>, URL)` - 引用远程服务
- `destroy()` - 销毁协议

---

### 2. ProxyFactory - 代理工厂

**接口**: `org.apache.dubbo.rpc.ProxyFactory`

**默认值**: `javassist`

**作用**: 动态代理工厂，创建服务代理

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| javassist | `org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory` | 使用 Javassist 字节码生成代理 |
| jdk | `org.apache.dubbo.rpc.proxy.jdk.JdkProxyFactory` | 使用 JDK 动态代理 |

**核心方法**:
- `getProxy(Invoker<T>)` - 获取服务代理
- `getInvoker(T, Class<T>, URL)` - 获取 Invoker

---

### 3. Filter - 过滤器

**接口**: `org.apache.dubbo.rpc.Filter`

**默认值**: 无，多个实现可同时激活

**作用**: RPC 调用过滤器，在调用前后执行

| 实现名称 | 用途 | 激活条件 |
|---------|------|---------|
| echo | 回声测试过滤器 | provider, group=provider |
| generic | 泛化调用过滤器 | provider, consumer |
| context | 上下文传递过滤器 | provider, consumer |
| exception | 异常转换过滤器 | provider |
| timeout | 超时过滤器 | consumer |
| monitor | 监控过滤器 | provider, consumer |
| trace | 链路追踪过滤器 | provider, consumer |
| validation | 参数验证过滤器 | consumer |
| cache | 缓存过滤器 | consumer |
| token | Token 验证过滤器 | provider |
| accesslog | 访问日志过滤器 | provider |
| classloader | 类加载器切换过滤器 | provider, consumer |

**核心方法**:
- `invoke(Invoker<T>, Invocation)` - 执行过滤

---

### 4. InvokerListener - Invoker 监听器

**接口**: `org.apache.dubbo.rpc.InvokerListener`

**作用**: 监听 Invoker 的引用和销毁事件

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| deprecated | `org.apache.dubbo.rpc.listener.DeprecatedInvokerListener` | 已废弃服务警告 |

---

### 5. ExporterListener - Exporter 监听器

**接口**: `org.apache.dubbo.rpc.ExporterListener`

**作用**: 监听服务导出和取消导出事件

---

## 注册中心相关 SPI

### 6. RegistryFactory - 注册中心工厂

**接口**: `org.apache.dubbo.registry.RegistryFactory`

**默认值**: 无

**作用**: 创建注册中心实例

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| zookeeper | `org.apache.dubbo.registry.zookeeper.ZookeeperRegistryFactory` | Zookeeper 注册中心 |
| nacos | `org.apache.dubbo.registry.nacos.NacosRegistryFactory` | Nacos 注册中心 |
| multicast | `org.apache.dubbo.registry.multicast.MulticastRegistryFactory` | 广播注册中心 |
| redis | `org.apache.dubbo.registry.redis.RedisRegistryFactory` | Redis 注册中心 |
| multiple | `org.apache.dubbo.registry.multiple.MultipleRegistryFactory` | 多注册中心聚合 |
| service-discovery-registry | `org.apache.dubbo.registry.client.ServiceDiscoveryRegistryFactory` | 应用级服务发现 |
| wrapper | `org.apache.dubbo.registry.RegistryFactoryWrapper` | 注册中心包装器 |

---

### 7. ServiceDiscoveryFactory - 服务发现工厂

**接口**: `org.apache.dubbo.registry.client.ServiceDiscoveryFactory`

**作用**: 创建服务发现实例

| 实现名称 | 实现类 |
|---------|--------|
| zookeeper | `org.apache.dubbo.registry.zookeeper.ZookeeperServiceDiscoveryFactory` |
| nacos | `org.apache.dubbo.registry.nacos.NacosServiceDiscoveryFactory` |
| multicast | `org.apache.dubbo.registry.multicast.MulticastServiceDiscoveryFactory` |

---

## 序列化相关 SPI

### 8. Serialization - 序列化

**接口**: `org.apache.dubbo.common.serialize.Serialization`

**默认值**: `hessian2`

**作用**: 对象序列化和反序列化

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| hessian2 | `org.apache.dubbo.common.serialize.hessian2.Hessian2Serialization` | Hessian2 序列化，默认 |
| java | `org.apache.dubbo.common.serialize.java.JavaSerialization` | JDK 原生序列化 |
| compactedjava | `org.apache.dubbo.common.serialize.java.CompactedJavaSerialization` | 压缩 JDK 序列化 |
| fastjson2 | `org.apache.dubbo.common.serialize.fastjson2.Fastjson2Serialization` | FastJSON2 序列化 |
| kryo | `org.apache.dubbo.common.serialize.kryo.KryoSerialization` | Kryo 序列化 |
| fst | `org.apache.dubbo.common.serialize.fst.FstSerialization` | FST 序列化 |
| protobuf | `org.apache.dubbo.common.serialize.protobuf.ProtobufSerialization` | Protobuf 序列化 |
| protobuf-json | `org.apache.dubbo.common.serialize.protobuf.support.ProtobufJsonSerialization` | Protobuf JSON 序列化 |
| avro | `org.apache.dubbo.common.serialize.avro.AvroSerialization` | Avro 序列化 |
| native-hessian | `org.apache.dubbo.common.serialize.nativehessian.NativeHessianSerialization` | Native Hessian 序列化 |

---

### 9. MultipleSerialization - 多序列化

**接口**: `org.apache.dubbo.common.serialize.MultipleSerialization`

**作用**: 支持多种序列化方式的组合

---

## 网络传输相关 SPI

### 10. Transporter - 网络传输层

**接口**: `org.apache.dubbo.remoting.Transporter`

**默认值**: `netty`

**作用**: 网络传输层抽象

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| netty | `org.apache.dubbo.remoting.transport.netty.NettyTransporter` | Netty 3 传输 |
| netty4 | `org.apache.dubbo.remoting.transport.netty4.NettyTransporter` | Netty 4 传输，推荐 |
| mina | `org.apache.dubbo.remoting.transport.mina.MinaTransporter` | Mina 传输 |
| grizzly | `org.apache.dubbo.remoting.transport.grizzly.GrizzlyTransporter` | Grizzly 传输 |

**核心方法**:
- `bind(URL, ChannelHandler)` - 绑定服务端
- `connect(URL, ChannelHandler)` - 连接客户端

---

### 11. Codec2 - 编解码器

**接口**: `org.apache.dubbo.remoting.Codec2`

**默认值**: `dubbo`

**作用**: 网络数据编解码

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| dubbo | `org.apache.dubbo.remoting.transport.codec.TransportCodec` | Dubbo 协议编解码 |
| telnet | `org.apache.dubbo.remoting.telnet.codec.TelnetCodec` | Telnet 协议编解码 |

---

### 12. Dispatcher - 线程调度器

**接口**: `org.apache.dubbo.remoting.Dispatcher`

**默认值**: `all`

**作用**: 决定消息如何派发到线程池

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| all | `org.apache.dubbo.remoting.transport.dispatcher.all.AllDispatcher` | 所有消息都派发到线程池 |
| direct | `org.apache.dubbo.remoting.transport.dispatcher.direct.DirectDispatcher` | 所有消息都在 IO 线程执行 |
| message | `org.apache.dubbo.remoting.transport.dispatcher.message.MessageOnlyDispatcher` | 只有请求响应消息派发到线程池 |
| execution | `org.apache.dubbo.remoting.transport.dispatcher.execution.ExecutionDispatcher` | 只有请求消息派发到线程池 |
| connection | `org.apache.dubbo.remoting.transport.dispatcher.connection.ConnectionOrderedDispatcher` | 连接上的消息有序 |

---

### 13. Exchanger - 信息交换层

**接口**: `org.apache.dubbo.remoting.exchange.Exchanger`

**默认值**: `header`

**作用**: 请求响应交换层

| 实现名称 | 实现类 |
|---------|--------|
| header | `org.apache.dubbo.remoting.exchange.support.header.HeaderExchanger` |

---

### 14. ZookeeperTransporter - Zookeeper 传输

**接口**: `org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter`

**默认值**: `curator`

**作用**: Zookeeper 客户端传输层

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| curator | `org.apache.dubbo.remoting.zookeeper.curator.CuratorZookeeperTransporter` | Curator 客户端 |
| curator5 | `org.apache.dubbo.remoting.zookeeper.curator5.Curator5ZookeeperTransporter` | Curator 5.x 客户端 |
| zkclient | `org.apache.dubbo.remoting.zookeeper.zkclient.ZkclientZookeeperTransporter` | ZkClient 客户端 |

---

### 15. TelnetHandler - Telnet 命令处理器

**接口**: `org.apache.dubbo.remoting.telnet.TelnetHandler`

**作用**: Telnet 命令处理

| 实现名称 | 用途 |
|---------|------|
| ls | 列出服务 |
| ps | 查看端口信息 |
| cd | 切换服务 |
| pwd | 显示当前服务 |
| count | 统计调用次数 |
| trace | 跟踪方法调用 |
| accesslog | 访问日志 |
| clear | 清除统计 |
| status | 查看状态 |
| log | 日志操作 |
| help | 帮助 |
| exit | 退出 |
| shutdown | 关闭 |

---

### 16. HttpBinder - HTTP 绑定器

**接口**: `org.apache.dubbo.remoting.http.HttpBinder`

**作用**: HTTP 服务端绑定

| 实现名称 | 实现类 |
|---------|--------|
| jetty | `org.apache.dubbo.remoting.http.jetty.JettyHttpBinder` |
| servlet | `org.apache.dubbo.remoting.http.servlet.ServletHttpBinder` |
| tomcat | `org.apache.dubbo.remoting.http.tomcat.TomcatHttpBinder` |

---

## 集群相关 SPI

### 17. Cluster - 集群

**接口**: `org.apache.dubbo.rpc.cluster.Cluster`

**默认值**: `failover`

**作用**: 集群容错策略

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| failover | `org.apache.dubbo.rpc.cluster.support.FailoverCluster` | 失败自动切换，默认 |
| failfast | `org.apache.dubbo.rpc.cluster.support.FailfastCluster` | 快速失败，只发起一次调用 |
| failsafe | `org.apache.dubbo.rpc.cluster.support.FailsafeCluster` | 安全失败，出现异常直接忽略 |
| failback | `org.apache.dubbo.rpc.cluster.support.FailbackCluster` | 失败自动恢复，后台记录失败请求 |
| forking | `org.apache.dubbo.rpc.cluster.support.ForkingCluster` | 并行调用多个服务器，只要一个成功即返回 |
| available | `org.apache.dubbo.rpc.cluster.support.AvailableCluster` | 遍历所有服务器，逐个尝试 |
| mergeable | `org.apache.dubbo.rpc.cluster.support.MergeableCluster` | 合并结果 |
| broadcast | `org.apache.dubbo.rpc.cluster.support.BroadcastCluster` | 广播调用所有提供者 |
| registry-aware | `org.apache.dubbo.rpc.cluster.support.RegistryAwareCluster` | 注册中心感知集群 |
| zone-aware | `org.apache.dubbo.rpc.cluster.support.registry.ZoneAwareCluster` | 可用区感知集群 |

---

### 18. LoadBalance - 负载均衡

**接口**: `org.apache.dubbo.rpc.cluster.LoadBalance`

**默认值**: `random`

**作用**: 负载均衡策略

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| random | `org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance` | 随机，默认 |
| roundrobin | `org.apache.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance` | 轮询 |
| leastactive | `org.apache.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance` | 最少活跃调用数 |
| consistenthash | `org.apache.dubbo.rpc.cluster.loadbalance.ConsistentHashLoadBalance` | 一致性 Hash |
| shortestresponse | `org.apache.dubbo.rpc.cluster.loadbalance.ShortestResponseLoadBalance` | 最短响应时间 |
| p2c | `org.apache.dubbo.rpc.cluster.loadbalance.P2CLoadBalance` | Power of Two Choices |
| adaptive | `org.apache.dubbo.rpc.cluster.loadbalance.AdaptiveLoadBalance` | 自适应负载均衡 |

---

### 19. Router - 路由

**接口**: `org.apache.dubbo.rpc.cluster.RouterFactory`

**作用**: 路由规则工厂

| 实现名称 | 用途 |
|---------|------|
| condition | 条件路由 |
| script | 脚本路由 |
| file | 文件路由 |
| tag | 标签路由 |
| app | 应用路由 |
| service | 服务路由 |

---

### 20. ConfiguratorFactory - 配置器工厂

**接口**: `org.apache.dubbo.rpc.cluster.ConfiguratorFactory`

**作用**: 动态配置规则工厂

| 实现名称 | 实现类 |
|---------|--------|
| override | `org.apache.dubbo.rpc.cluster.configurator.override.OverrideConfiguratorFactory` |
| absent | `org.apache.dubbo.rpc.cluster.configurator.absent.AbsentConfiguratorFactory` |

---

### 21. Merger - 结果合并器

**接口**: `org.apache.dubbo.rpc.cluster.Merger`

**作用**: 合并返回结果

| 实现名称 | 用途 |
|---------|------|
| array | 数组合并 |
| boolean | Boolean 合并 |
| byte | Byte 合并 |
| char | Char 合并 |
| double | Double 合并 |
| float | Float 合并 |
| int | Int 合并 |
| list | List 合并 |
| long | Long 合并 |
| map | Map 合并 |
| set | Set 合并 |
| short | Short 合并 |

---

### 22. ClusterFilter - 集群过滤器

**接口**: `org.apache.dubbo.rpc.cluster.filter.ClusterFilter`

**作用**: 集群层面的过滤器

---

## 配置中心相关 SPI

### 23. DynamicConfigurationFactory - 动态配置工厂

**接口**: `org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory`

**默认值**: `nop`

**作用**: 动态配置中心工厂

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| zookeeper | `org.apache.dubbo.configcenter.zookeeper.ZookeeperDynamicConfigurationFactory` | Zookeeper 配置中心 |
| nacos | `org.apache.dubbo.configcenter.nacos.NacosDynamicConfigurationFactory` | Nacos 配置中心 |
| apollo | `org.apache.dubbo.configcenter.apollo.ApolloDynamicConfigurationFactory` | Apollo 配置中心 |
| consul | `org.apache.dubbo.configcenter.consul.ConsulDynamicConfigurationFactory` | Consul 配置中心 |
| etcd3 | `org.apache.dubbo.configcenter.etcd3.Etcd3DynamicConfigurationFactory` | Etcd3 配置中心 |
| nop | `org.apache.dubbo.common.config.configcenter.nop.NopDynamicConfigurationFactory` | 空实现 |
| spring-cloud | `org.apache.dubbo.configcenter.springcloud.SpringCloudConfigConfigurationFactory` | Spring Cloud Config |

---

## 元数据相关 SPI

### 24. MetadataReportFactory - 元数据报告工厂

**接口**: `org.apache.dubbo.metadata.report.MetadataReportFactory`

**作用**: 元数据报告工厂

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| zookeeper | `org.apache.dubbo.metadata.report.zookeeper.ZookeeperMetadataReportFactory` | Zookeeper 元数据中心 |
| redis | `org.apache.dubbo.metadata.report.redis.RedisMetadataReportFactory` | Redis 元数据中心 |
| nacos | `org.apache.dubbo.metadata.report.nacos.NacosMetadataReportFactory` | Nacos 元数据中心 |

---

### 25. ServiceNameMapping - 服务名映射

**接口**: `org.apache.dubbo.metadata.ServiceNameMapping`

**作用**: 服务接口名到应用名的映射

| 实现名称 | 实现类 |
|---------|--------|
| default | `org.apache.dubbo.metadata.ServiceNameMappingDefaultImpl` |

---

### 26. MetadataParamsFilter - 元数据参数过滤器

**接口**: `org.apache.dubbo.metadata.MetadataParamsFilter`

**作用**: 元数据参数过滤

---

### 27. TypeBuilder - 类型定义构建器

**接口**: `org.apache.dubbo.metadata.definition.builder.TypeBuilder`

**作用**: 构建类型定义

| 实现名称 | 用途 |
|---------|------|
| array | 数组类型 |
| collection | 集合类型 |
| enum | 枚举类型 |
| map | Map 类型 |
| class | 类类型 |

---

## 监控与指标相关 SPI

### 28. MonitorFactory - 监控工厂

**接口**: `org.apache.dubbo.monitor.MonitorFactory`

**默认值**: `dubbo`

**作用**: 监控工厂

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| dubbo | `org.apache.dubbo.monitor.dubbo.DubboMonitorFactory` | Dubbo 监控中心 |

---

### 29. MetricsCollector - 指标收集器

**接口**: `org.apache.dubbo.metrics.collector.MetricsCollector`

**作用**: 指标收集器

| 实现名称 | 实现类 |
|---------|--------|
| default | `org.apache.dubbo.metrics.collector.DefaultMetricsCollector` |
| config-center | `org.apache.dubbo.metrics.collector.ConfigCenterMetricsCollector` |
| metadata | `org.apache.dubbo.metrics.collector.MetadataMetricsCollector` |
| registry | `org.apache.dubbo.metrics.collector.RegistryMetricsCollector` |

---

### 30. MetricsReporterFactory - 指标报告工厂

**接口**: `org.apache.dubbo.metrics.report.MetricsReporterFactory`

**作用**: 指标报告工厂

| 实现名称 | 实现类 |
|---------|--------|
| default | `org.apache.dubbo.metrics.report.DefaultMetricsReporterFactory` |
| prometheus | `org.apache.dubbo.metrics.prometheus.PrometheusMetricsReporterFactory` |

---

### 31. MetricsService - 指标服务

**接口**: `org.apache.dubbo.metrics.service.MetricsService`

**作用**: 指标服务

| 实现名称 | 实现类 |
|---------|--------|
| default | `org.apache.dubbo.metrics.service.DefaultMetricsService` |

---

## 过滤器相关 SPI

### 32. CacheFactory - 缓存工厂

**接口**: `org.apache.dubbo.cache.CacheFactory`

**默认值**: `lru`

**作用**: 缓存工厂

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| lru | `org.apache.dubbo.cache.support.lru.LruCacheFactory` | 最近最少使用缓存 |
| threadlocal | `org.apache.dubbo.cache.support.threadlocal.ThreadLocalCacheFactory` | ThreadLocal 缓存 |
| jcache | `org.apache.dubbo.cache.support.jcache.JCacheFactory` | JCache 缓存 |
| expiring | `org.apache.dubbo.cache.support.expiring.ExpiringCacheFactory` | 过期缓存 |

---

### 33. Validation - 验证器

**接口**: `org.apache.dubbo.validation.Validation`

**默认值**: `jvalidation`

**作用**: 参数验证器

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| jvalidation | `org.apache.dubbo.validation.support.jvalidation.JValidation` | JSR-303 验证 |

---

## 其他通用 SPI

### 34. ThreadPool - 线程池

**接口**: `org.apache.dubbo.common.threadpool.ThreadPool`

**默认值**: `fixed`

**作用**: 线程池实现

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| fixed | `org.apache.dubbo.common.threadpool.support.fixed.FixedThreadPool` | 固定大小线程池，默认 |
| cached | `org.apache.dubbo.common.threadpool.support.cached.CachedThreadPool` | 缓存线程池 |
| limited | `org.apache.dubbo.common.threadpool.support.limited.LimitedThreadPool` | 可伸缩但上限线程池 |
| eager | `org.apache.dubbo.common.threadpool.support.eager.EagerThreadPool` | 优先创建线程的线程池 |

---

### 35. Compiler - 编译器

**接口**: `org.apache.dubbo.common.compiler.Compiler`

**默认值**: `javassist`

**作用**: 动态编译器

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| javassist | `org.apache.dubbo.common.compiler.support.JavassistCompiler` | Javassist 编译，默认 |
| jdk | `org.apache.dubbo.common.compiler.support.JdkCompiler` | JDK 编译 |
| adaptive | `org.apache.dubbo.common.compiler.support.AdaptiveCompiler` | 自适应编译器 |

---

### 36. StatusChecker - 状态检查器

**接口**: `org.apache.dubbo.common.status.StatusChecker`

**作用**: 状态检查

| 实现名称 | 用途 |
|---------|------|
| memory | 内存状态 |
| load | 系统负载 |
| server | 服务端状态 |
| registry | 注册中心状态 |
| spring | Spring 状态 |
| datasource | 数据源状态 |

---

### 37. LoggerAdapter - 日志适配器

**接口**: `org.apache.dubbo.common.logger.LoggerAdapter`

**默认值**: `slf4j`

**作用**: 日志适配器

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| slf4j | `org.apache.dubbo.common.logger.slf4j.Slf4jLoggerAdapter` | SLF4J 日志 |
| log4j | `org.apache.dubbo.common.logger.log4j.Log4jLoggerAdapter` | Log4j 日志 |
| log4j2 | `org.apache.dubbo.common.logger.log4j2.Log4j2LoggerAdapter` | Log4j2 日志 |
| jcl | `org.apache.dubbo.common.logger.jcl.JclLoggerAdapter` | JCL 日志 |
| jdk | `org.apache.dubbo.common.logger.jdk.JdkLoggerAdapter` | JDK 日志 |

---

### 38. ExtensionInjector - 扩展注入器

**接口**: `org.apache.dubbo.common.extension.ExtensionInjector`

**作用**: 扩展依赖注入

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| spi | `org.apache.dubbo.common.extension.inject.SpiExtensionInjector` | SPI 扩展注入 |
| spring | `org.apache.dubbo.config.spring.extension.SpringExtensionInjector` | Spring Bean 注入 |
| adaptive | `org.apache.dubbo.common.extension.inject.AdaptiveExtensionInjector` | 自适应注入器 |

---

### 39. Container - 容器

**接口**: `org.apache.dubbo.container.Container`

**默认值**: `spring`

**作用**: 服务容器

| 实现名称 | 实现类 | 用途 |
|---------|--------|------|
| spring | `org.apache.dubbo.container.spring.SpringContainer` | Spring 容器，默认 |
| log4j | `org.apache.dubbo.container.log4j.Log4jContainer` | Log4j 容器 |
| logback | `org.apache.dubbo.container.logback.LogbackContainer` | Logback 容器 |
| jetty | `org.apache.dubbo.container.jetty.JettyContainer` | Jetty 容器 |

---

### 40. DataStore - 数据存储

**接口**: `org.apache.dubbo.common.store.DataStore`

**作用**: 数据存储

| 实现名称 | 实现类 |
|---------|--------|
| simple | `org.apache.dubbo.common.store.support.SimpleDataStore` |

---

### 41. InfraAdapter - 基础设施适配器

**接口**: `org.apache.dubbo.common.infra.InfraAdapter`

**作用**: 基础设施信息获取

| 实现名称 | 实现类 |
|---------|--------|
| os | `org.apache.dubbo.common.infra.support.EnvironmentAdapter` |
| env | `org.apache.dubbo.common.infra.support.EnvironmentAdapter` |
| aws | `org.apache.dubbo.common.infra.support.AwsEc2MetadataAdapter` |
| azure | `org.apache.dubbo.common.infra.support.AzureMetadataAdapter` |
| aliyun | `org.apache.dubbo.common.infra.support.AliyunEcsMetadataAdapter` |

---

### 42. CertProvider - 证书提供者

**接口**: `org.apache.dubbo.common.ssl.CertProvider`

**作用**: SSL 证书提供者

---

### 43. ExecutorRepository - 执行器仓库

**接口**: `org.apache.dubbo.common.threadpool.manager.ExecutorRepository`

**作用**: 线程池执行器管理

| 实现名称 | 实现类 |
|---------|--------|
| default | `org.apache.dubbo.common.threadpool.manager.DefaultExecutorRepository` |

---

### 44. IsolationExecutorSupportFactory - 隔离执行器支持工厂

**接口**: `org.apache.dubbo.rpc.executor.IsolationExecutorSupportFactory`

**作用**: 线程隔离执行器

---

### 45. ApplicationDeployListener - 应用部署监听器

**接口**: `org.apache.dubbo.common.deploy.ApplicationDeployListener`

**作用**: 应用部署生命周期监听

---

### 46. ModuleDeployListener - 模块部署监听器

**接口**: `org.apache.dubbo.common.deploy.ModuleDeployListener`

**作用**: 模块部署生命周期监听

---

### 47. ApplicationExt - 应用扩展

**接口**: `org.apache.dubbo.common.context.ApplicationExt`

**作用**: 应用级扩展

---

### 48. ModuleExt - 模块扩展

**接口**: `org.apache.dubbo.common.context.ModuleExt`

**作用**: 模块级扩展

---

### 49. Converter - 类型转换器

**接口**: `org.apache.dubbo.common.convert.Converter`

**作用**: 类型转换

---

### 50. MultiValueConverter - 多值转换器

**接口**: `org.apache.dubbo.common.convert.multiple.MultiValueConverter`

**作用**: 多值类型转换

---

## 总结

Dubbo 的 SPI 机制提供了非常强大的扩展能力，覆盖了 RPC、注册中心、序列化、网络传输、集群、配置中心、监控等各个方面。开发者可以根据需要实现这些 SPI 接口来定制 Dubbo 的行为。

### 关键特性总结

1. **按需加载**：只在使用时才加载扩展实现
2. **默认实现**：每个 SPI 都有默认实现
3. **作用域隔离**：支持框架、应用、模块三级作用域
4. **依赖注入**：扩展之间可以自动注入
5. **包装机制**：支持 Wrapper 模式
6. **自适应扩展**：根据 URL 动态选择
7. **激活机制**：根据条件自动激活

