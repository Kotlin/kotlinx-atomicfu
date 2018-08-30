# AtomicFU 

[![JetBrains incubator project](http://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://api.bintray.com/packages/kotlin/kotlinx/kotlinx.atomicfu/images/download.svg) ](https://bintray.com/kotlin/kotlinx/kotlinx.atomicfu/_latestVersion)

The idiomatic way to use atomic operations in Kotlin. 

* Code it like `AtomicReference/Int/Long`, but run it in production efficiently as `AtomicXxxFieldUpdater` on Kotlin/JVM 
  and as plain unboxed values on Kotlin/JS. 
* Use Kotlin-specific extensions (e.g. inline `updateAndGet` and `getAndUpdate` functions).
* Compile-time dependency only (no runtime dependencies).
  * Post-compilation bytecode transformer that declares all the relevant field updaters for you on [Kotlin/JVM](#jvm).
  * Post-compilation JavaScript files transformer on [Kotlin/JS](#js).
* Multiplatform: 
  * [Kotlin/Native](#native) is supported.
  * However, Kotlin/Native works as library dependency at the moment (unlike Kotlin/JVM and Kotlin/JS).
  * This enables writing [common](#common) Kotlin code with atomics that compiles for JVM, JS, and Native.
* [Gradle](#gradle-build-setup) for all platforms and [Maven](#maven-build-setup) for JVM are supported.  
* [Additional features](#additional-features) include:
  * Support for [JDK9 VarHandle](#varhandles-with-java-9).
  * Support for [testing of lock-free data structures](#testing-lock-free-data-structures-on-jvm).

## Example

Let us declare a `top` variable for a lock-free stack implementation:

```kotlin
import kotlinx.atomicfu.* // import top-level functions from kotlinx.atomicfu

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

* Declare atomic variables as `private val` or `internal val`. You can use just (public) `val`, 
  but make sure they are not directly accessed outside of your Kotlin module (outside of the source set).
  Access to the atomic variable itself shall be encapsulated.
* Only simple operations on atomic variables _directly_ are supported. 
  * Do not read references on atomic variables into local variables,
    e.g. `top.compareAndSet(...)` is Ok, while `val tmp = top; tmp...` is not. 
  * Do not leak references on atomic variables in other way (return, pass as params, etc). 
* Do not introduce complex data flow in parameters to atomic variable operations, 
  i.e. `top.value = complex_expression` and `top.compareAndSet(cur, complex_expression)` are not supported 
  (more specifically, `complex_expression` should not have branches in its compiled representation).
  Extract `complex_expression` into a variable when needed.
* Use the following convention if you need to expose the value of atomic property to the public:

```kotlin
private val _foo = atomic<T>(initial) // private atomic, convention is to name it with leading underscore
public var foo: T                     // public val/var
    get() = _foo.value
    set(value) { _foo.value = value }
```  

## Gradle build setup

Building with Gradle is supported for all platforms.

### JVM

You will need Gradle 4.0 or later for the following snippets to work.
Add and apply AtomicFU plugin:

```groovy
buildscript {
    ext.atomicfu_version = '0.11.3'

    dependencies {
        classpath "org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version"
    }
}

apply plugin: 'kotlinx-atomicfu'
```

Add compile-only dependency on AtomicFU library and run-time dependency for tests:

```groovy
dependencies {
    compileOnly "org.jetbrains.kotlinx:atomicfu:$atomicfu_version"
    testRuntime "org.jetbrains.kotlinx:atomicfu:$atomicfu_version"
}
```
  
### JS 

Configure add apply plugin just like for [JVM](#jvm). 
The only change is that JS version of the library (`atomicfu-js`) shall be used in dependencies:

```groovy
dependencies {
    compileOnly "org.jetbrains.kotlinx:atomicfu-js:$atomicfu_version"
    testRuntime "org.jetbrains.kotlinx:atomicfu-js:$atomicfu_version"
}
```

### Native

This library is available for Kotlin/Native (`atomicfu-native`).
It is a regular library and you should declare a normal dependency, no plugin is needed nor available.
Only single-threaded code (JS-style) is currently supported. 

Kotlin/Native supports only Gradle version 4.7 or later 
and you should use `kotlin-platform-native` plugin.

First of all, you'll need to enable Gradle metadata in your
`settings.gradle` file:

```groovy
enableFeaturePreview('GRADLE_METADATA')
```

Then, you'll need to apply the corresponding plugin and add appropriate dependencies in your
`build.gradle` file:

```groovy
buildscript {
    repositories {
        jcenter()
        maven { url 'https://plugins.gradle.org/m2/' }
        maven { url 'https://dl.bintray.com/jetbrains/kotlin-native-dependencies' }
    }

    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-native-gradle-plugin:$kotlin_native_version"
    }

}

apply plugin: 'kotlin-platform-native'

repositories {
    jcenter()
}

dependencies {
    implementation 'org.jetbrains.kotlinx:atomicfu-native:0.11.3'
}

sourceSets {
    main {
        component {
            target "ios_arm64", "ios_arm32", "ios_x64", "macos_x64", "linux_x64", "mingw_x64" 
            outputKinds = [EXECUTABLE]
        }
    }
}
```

Since Kotlin/Native does not generally provide binary compatibility between versions, 
you should use the same version of Kotlin/Native compiler as was used to build AtomicFU. 
Add an appropriate `kotlin_native_version` to your `gradle.properties` file. 
See [gradle.properties](gradle.properties) in AtomicFU project.

### Common

If you write a common code that should get compiled or different platforms, add `org.jetbrains.kotlinx:atomicfu-common`
to your common code dependencies:

```groovy
dependencies {
    compile "org.jetbrains.kotlinx:atomicfu-common:$atomicfu_version"
}
```

## Maven build setup

Declare AtomicFU version:

```xml
<properties>
     <atomicfu.version>0.11.3</atomicfu.version>
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

## Additional features

AtomicFU provides some additional features that you can optionally use.

### VarHandles with Java 9

AtomicFU can produce code that uses Java 9 
[VarHandle](http://download.java.net/java/jdk9/docs/api/java/lang/invoke/VarHandle.html)
instead of `AtomicXxxFieldUpdater`. Configure transformation `variant` in Gradle build file:
 
```groovy
atomicfu {
    variant = "VH"
}
``` 
 
It can also create [JEP 238](http://openjdk.java.net/jeps/238) multi-release jar file with both
`AtomicXxxFieldUpdater` for JDK<=8 and `VarHandle` for for JDK9+ if you 
set `variant` to `"BOTH"`.

### Testing lock-free data structures on JVM

You can optionally test lock-freedomness of lock-free data structures using `LockFreedomTestEnvironment` class.
See example in [`LockFreeQueueLFTest`](atomicfu/src/test/kotlin/kotlinx/atomicfu/test/LockFreeQueueLFTest.kt).
Testing is performed by pausing one (random) thread before or after a random state-update operation and
making sure that all other threads can still make progress. 

In order to make those test to actually perform lock-freedomness testing you need to configure an additional 
execution of tests with the original (non-transformed) classes for Maven:

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

For Gradle there nothing else to add. Tests are always run using original (non-transformed) classes.




