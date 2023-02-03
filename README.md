# 基于字节码插桩实现的简易零侵入性能监控框架

## 一、缘起

从了解如何获取Java代码运行时间开始，我就一直觉得这种通过`改动代码`实现的性能监控极其繁琐，后来学习到`Proxy`，`Spring aop`这些通过代理动态增强代码功能的方式，但还是不够简便，最近看lombok实现原理的时候，了解到了字节码插桩技术，通过`javaagent`+`javassist`的方式，可以很方便的为整个项目增强功能。于是这个框架就出现了...



## 二、实现过程

### 1.引入依赖

首先建立一个maven项目，pom依赖如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>cn.lyxlz</groupId>
    <artifactId>runtime</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.24</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>1.6.4</version>
        </dependency>
        <dependency>
            <groupId>org.javassist</groupId>
            <artifactId>javassist</artifactId>
            <version>3.29.2-GA</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>RELEASE</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.0.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                        </manifest>
                        <manifestEntries>
                            <Premain-Class>
                                cn.lyxlz.runtime.Proxy
                            </Premain-Class>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

> 这里要注意打包插件需要配置`Premain-Class`，用来标注javaagent入口方法位置

### 2.实现代码

为了提高框架运用的灵活性，这里采用注解的方式标识需要增强的类

#### 创建注解RunTime

```java
@Target({ElementType.METHOD})
public @interface RunTime {
}
```

#### 创建Proxy类编写业务代码

其中，javaagent标识的入口方法为`public static void premain(String args, Instrumentation inst)`，

```java
@Slf4j
public class Proxy {
    public static void premain(String args, Instrumentation inst) {
        // 添加类加载过滤器
        inst.addTransformer((loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            // 创建类池
            ClassPool pool = ClassPool.getDefault();
            try {
                // 获取当前加载的类
                CtClass ctClass = pool.get(className.replaceAll("/", "."));
                // 获取类中的所有方法
                CtMethod[] methods = ctClass.getMethods();
                for (CtMethod method : methods) {
                    log.info("当前方法：{}", method.getName());
                    // 判断是否包含指定注解
                    Object annotation = method.getAnnotation(RunTime.class);
                    if (annotation == null) continue;
                    // 如果包含，先复制一份方法，方便方法前后位置的增强
                    CtMethod copyMethod = CtNewMethod.copy(method, ctClass, null);
                    // 设置被增强方法名
                    String copyMethodName = copyMethod.getName() + "$agent";
                    copyMethod.setName(copyMethodName);
                    // 将agent方法添加回类
                    ctClass.addMethod(copyMethod);
                    // 改变原有方法
                    log.info("正在增强{}方法", method.getName());
                    method.setBody("{ " +
                            "long begin = System.currentTimeMillis();" +
                            copyMethodName + "();" +
                            "System.out.print(\"程序运行：\");" +
                            "System.out.println(System.currentTimeMillis() - begin + \" ms\");" +
                            " }");
                    return ctClass.toBytecode();
                }
            } catch (Exception ignored) {

            }
            return null;
        });
    }
}
```



## 三、在IDEA中使用

### 0.手动添加jar包到本地maven

由于maven公共仓库的提交流程过于繁琐，于是用相对简单的方式直接把jar包提交到了github，通过mvn命令将jar包手动添加到本地仓库，请运行一下命令

```bash
mvn install:install-file -Dfile=(jar包的位置) -DgroupId=cn.lyxlz -DartifactId=runtime -Dversion=1.0-GA -Dpackaging=jar
```

### 1.引入依赖

```xml
<dependency>
    <groupId>cn.lyxlz</groupId>
    <artifactId>runtime</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2.编写测试方法

```java
public class Demo {
    public static void main(String[] args) {
        test();
    }

    @RunTime
    public static void test() {
        try {
            TimeUnit.SECONDS.sleep(1);
            System.out.println("test1运行完毕");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 3.修改IDEA运行配置

选择 Edit Configurations

![image-20230203113326949](https://img-1304774017.cos.ap-nanjing.myqcloud.com/img/image-20230203113326949.png)

选择Edit configuration templates，编辑运行模板

![image-20230203113459102](https://img-1304774017.cos.ap-nanjing.myqcloud.com/img/image-20230203113459102.png)

选择`Application`，在右侧`Add Run Options`选项中选择`Add VM options`

![image-20230203113618834](https://img-1304774017.cos.ap-nanjing.myqcloud.com/img/image-20230203113618834.png)

添加VM Option：

```sh
-javaagent:xxx/xxx.jar # 你的maven仓库中runtime jar包的位置
```

然后点击运行即可

![image-20230203113900068](https://img-1304774017.cos.ap-nanjing.myqcloud.com/img/image-20230203113900068.png)

> 注意，此配置下所有IDEA项目都会带着runtime的javaagent选项，只要不使用@Runtime注解则不会对元项目有影响