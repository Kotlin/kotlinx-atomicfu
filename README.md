# AtomicFU 

[![JetBrains incubator project](http://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://api.bintray.com/packages/kotlin/kotlinx/kotlinx.atomicfu/images/download.svg) ](https://bintray.com/kotlin/kotlinx/kotlinx.atomicfu/_latestVersion)

The idiomatic way to use atomic operations in Kotlin. 

* Code it like `AtomicReference/Int/Long`, but run it in production like `AtomicReference/Int/LongFieldUpdater`. 
* Use Kotlin-specific extensions (e.g. inline `updateAndGet` and `getAndUpdate` functions).
* Compile-time dependency only (no runtime dependencies).
* Post-compilation bytecode transformer that declares all the relevant field updaters for you. 

## Example

Let us declare a `top` variable for a lock-free stack implementation:

```kotlin
import kotlinx.atomicfu.atomic // import top-level atomic function from kotlinx.atomicfu

private val top = atomic<Node?>(null) 
```

Use `top.value` to perform volatile reads and writes:

```kotlin
fun isEmpty() = top.value == null  // volatile read
fun clear() { top.value = null }   // volatile write
``` 

Use `compareAndSet` function directly:

```kotlin
if (top.compareAndSet(expect, update)) ... 
```

Use higher-level looping primitives (inline extensions), for example:

```kotlin
top.loop { cur ->   // while(true) loop that volatile-reads current value 
   ...
}
```

Use high-level `update`, `updateAndGet`, and `getAndUpdate`, 
when possible, for idiomatic lock-free code, for example:

```kotlin
fun push(v: Value) = top.update { cur -> Node(v, cur) }
fun pop(): Value? = top.getAndUpdate { cur -> cur?.next } ?.value
```

Declare atomic integers and longs using type inference:

```kotlin
val myInt = atomic(0)    // note: integer initial value
val myLong = atomic(0L)  // note: long initial value   
```

Integer and long atomics provide all the usual `getAndIncrement`, `incrementAndGet`, `getAndAdd`, `addAndGet`, and etc
operations. They can be also atomically modified via `+=` and `-=` operators.

## Dos and Don'ts

* Declare atomic variables as `private val` or `internal val`. You can use just (public) `val` in nested classes, 
  but make sure they are not accessed outside of your Kotlin source file.
* Only simple operations on atomic variables _directly_ are supported. 
  * Do not read references on atomic variables into local variables,
    e.g. `top.compareAndSet(...)` is Ok, while `val tmp = top; tmp...` is not. 
  * Do not leak references on atomic variables in other way (return, pass as params, etc). 
* Do not introduce complex data flow in parameters to atomic variable operations, 
  i.e. `top.value = complex_expression` and `top.compareAndSet(cur, complex_expression)` are not supported 
  (more specifically, `complex_expression` should not have branches in its compiled representation).
* Use the following convention if you need to expose the value of atomic property to the public:

```kotlin
private val _foo = atomic<T>(initial) // private atomic, convention is to name it with leading underscore
public var foo: T                     // public val/var
    get() = _foo.value
    set(value) { _foo.value = value }
```  

## Maven build setup

Declare AtomicFU version:

```xml
<properties>
     <atomicfu.version>0.10.3</atomicfu.version>
</properties> 
```

Declare _provided_ dependency on the AtomicFU library 
(the users of the resulting artifact will not have a dependency on AtomicFU library):

```xml
    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlinx</groupId>
            <artifactId>atomicfu</artifactId>
            <version>${atomicfu.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
```

Configure build steps so that Kotlin compiler puts classes into a different `classes-pre-atomicfu` directory,
which is then transformed to a regular `classes` directory to be used later by tests and delivery.

```xml
    <build>
        <plugins>
            <!-- compile Kotlin files to staging directory -->
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <output>${project.build.directory}/classes-pre-atomicfu</output>
                            <!-- "VH" to use Java 9 VarHandle, "BOTH" to produce multi-version code -->
                            <variant>FU</variant>  
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- transform classes with AtomicFU plugin -->
            <plugin>
                <groupId>org.jetbrains.kotlinx</groupId>
                <artifactId>atomicfu-maven-plugin</artifactId>
                <version>${atomicfu.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>transform</goal>
                        </goals>
                        <configuration>
                            <input>${project.build.directory}/classes-pre-atomicfu</input>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

## Gradle build setup

You will need Gradle 4.0 or later for the following snippets to work.
Add and apply AtomicFU plugin:

```groovy
buildscript {
    ext.atomicfu_version = '0.10.3'

    dependencies {
        classpath "org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version"
    }
}

apply plugin: 'kotlinx-atomicfu'
```

Add compile-only dependency on AtomicFU library:

```groovy
dependencies {
    compileOnly "org.jetbrains.kotlinx:atomicfu:$atomicfu_version"
}
```

Install bytecode transformation pipeline so that compiled classes from `classes` directory get 
transformed to a different `classes-post-atomicfu` directory to be used later by tests and delivery.

```groovy
def CLASSES_POST_ATOMICFU = file("$buildDir/classes-post-atomicfu/main")

atomicFU {
    inputFiles = sourceSets.main.output.classesDirs
    outputDir = CLASSES_POST_ATOMICFU
    classPath = sourceSets.main.runtimeClasspath
    variant = "FU" // "VH" to use Java 9 VarHandle, "BOTH" to produce multi-version code
}

atomicFU.dependsOn compileKotlin
testClasses.dependsOn atomicFU
jar.dependsOn atomicFU

jar {
    mainSpec.sourcePaths.clear() // hack to clear existing paths
    from files(CLASSES_POST_ATOMICFU, sourceSets.main.output.resourcesDir)
}
```

## VarHandles with Java 9 (optional)

AtomicFU can produce code that is using Java 9 
[VarHandle](http://download.java.net/java/jdk9/docs/api/java/lang/invoke/VarHandle.html)
instead of `AtomicXXXFieldUpdater`. Set `variant` configuration option to `VH`.  

You can also create [JEP 238](http://openjdk.java.net/jeps/238) multi-release jar with both
`AtomicXXXFieldUpdater` baseline and `VarHandle` version for Java 9+. 
Set `variant` configuration option to `BOTH` and configure `Multi-Release: true` attribute
in the resulting jar manifest.

## Testing lock-free data structures (optional)

You can optionally test lock-freedomness of lock-free data structures using `LockFreedomTestEnvironment` class.
See example in [`LockFreeQueueLFTest`](atomicfu-test/src/test/kotlin/kotlinx/atomicfu/test/LockFreeQueueLFTest.kt).
Testing is performed by pausing one (random) thread before or after a random state-update operation and
making sure that all other threads can still make progress. 

In order to make those test to actually perform lock-freedomness testing you need to configure an additional 
execution of tests with the original (non-transformed) classes.

For Maven add:

```xml
    <build>
        <plugins>
            <!-- additional test execution with surefire on non-transformed files -->
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <executions>
                    <execution>
                        <id>lockfree-test</id>
                        <phase>test</phase>
                        <goals>
                            <goal>test</goal>
                        </goals>
                        <configuration>
                            <classesDirectory>${project.build.directory}/classes-pre-atomicfu</classesDirectory>
                            <includes>
                                <include>**/*LFTest.*</include>
                            </includes>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

For Gradle add:

```groovy
dependencies {
    testRuntime "org.jetbrains.kotlinx:atomicfu:$atomicfu_version"
}

task lockFreedomTest(type: Test, dependsOn: testClasses) {
    include '**/*LFTest.*'
}
```




