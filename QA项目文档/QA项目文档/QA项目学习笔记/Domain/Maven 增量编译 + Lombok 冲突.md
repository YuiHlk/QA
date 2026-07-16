# 手写 getter 解决 Lombok + Maven 增量编译 bug

## 1. 代码作用

```java
// 显式getter — 避免Maven编译器增量编译顺序问题导致Lombok生成的方法不可见
public Integer getChunkOverlap() {
    return chunkOverlap;
}
```

手动手写 `chunkOverlap` 的 getter，不用 Lombok `@Data/@Getter` 自动生成，专门规避 Maven 增量编译的经典坑。

## 2. 问题根源：Maven 增量编译 + Lombok 冲突

### 1）两个核心机制的工作逻辑

1. **Lombok**
    
    属于 `Annotation Processor` 注解处理器，**编译早期阶段**修改 AST 抽象语法树，自动生成 get/set/toString 等方法。
    
    只有 javac 完整走一遍注解处理流程，才能生成对应方法。（Lombok 生成的方法不是永久保存在文件里，**每次 javac 完整扫描该类时才会重新生成**）
    
2. **Maven 增量编译（maven-compiler-plugin 默认开启）**
    
    Maven 会缓存上一次编译的源文件、class 文件，当你只修改少量代码时，**只编译变更相关类**，跳过全量编译来提速。
    
    增量编译会优化编译执行顺序、跳过部分注解处理流程。
    

### 2）冲突场景

当项目存在**跨类依赖调用**：

A 类（Service）调用 `B实体.getChunkOverlap()`

B 实体只用`@Data`，靠 Lombok 自动生成 getter

增量编译触发流程：

1. 你修改了 A 类，没改 B 类；
2. Maven 判定只需要重新编译 A；
3. 编译 A 时，加载旧的 B 的缓存元数据；
4. 此时 Lombok 注解处理器不会重新执行、不会重新生成 B 的 getter；
5. 编译器扫描 A 代码，发现找不到`getChunkOverlap()`，直接报编译错误：`找不到符号`。

> IDEA 内置编译器不会复现，只有 `mvn compile / mvn package` 打包时才报错，是典型「本地能跑、打包报错」诡异问题。

### 3）两者冲突的核心矛盾

- Lombok 的方法**依赖本次 javac 完整处理当前类才能生成**；
- Maven 增量编译为了提速，主动跳过未修改类的 javac 执行；
- 跳过 → Lombok 不运行 → 动态生成的方法不存在 → 编译报错。

#### 重点：
缓存文件存放在 `target/maven-status/maven-compiler-plugin/`，它的生成逻辑极度简单粗暴：
Maven 不会启动 javac、不会运行 Lombok、不会解析编译后的 class 文件。
它只做一件事：**直接读取你本地的 `.java` 文本文件**，扫描里面手写的内容，提取信息存入缓存摘要，用于快速做语法校验

## 3. 两种解决方案对比

### 方案 1：局部手写 getter（你当前代码用的方案）

优点：

- 不用改全局编译配置，单类局部修复；
- 不影响其他字段继续用 Lombok；
- 团队不用统一调整 pom，侵入最小。
    
    缺点：需要手动维护少量 getter，字段修改时要同步改方法。

### 方案 2：关闭 Maven 增量编译（全局根治）

在 `pom.xml` 的 maven-compiler-plugin 关闭增量编译：

xml

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <compilerArgs>
            <arg>-Dmaven.compiler.incremental=false</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

优点：一劳永逸，所有类不再出现该问题；

缺点：项目大时，局部修改会触发更多编译，编译速度变慢。

## 4. 结合项目业务场景

`chunkOverlap` 是 RAG 分块重叠长度，大量 Service、工具类会频繁读取这个字段；

如果编译时经常报找不到这个 getter，开发、打包、CI 流水线都会报错，所以开发人员手动手写这个 getter 规避编译故障。

## 5. 补充说明

1. 这个问题**只在 Maven 增量编译下出现**，IDEA 单独编译运行基本不会复现；
2. Gradle 项目几乎没有该 Lombok 编译顺序问题；
3. 同类坑：静态常量、嵌套实体、循环依赖实体，更容易触发增量编译 Lombok 失效；
4. 规范建议：
    
    - 频繁被大量类调用的核心字段，可手动写 getter/setter；
    - 或者直接全局关闭 Maven 增量编译，彻底杜绝这类诡异编译报错。