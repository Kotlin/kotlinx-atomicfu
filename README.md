# AtomicFU 

[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
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
  * [JDK9 VarHandle](#varhandles-with-java-9).
  * [Arrays of atomic values](#arrays-of-atomic-values).
  * [User-defined extensions on atomics](#user-defined-extensions-on-atomics)
  * [Locks](#locks)
  * [Testing of lock-free data structures](#testing-lock-free-data-structures-on-jvm).

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

You will need Gradle 4.10 or later.
Add and apply AtomicFU plugin. It adds all the corresponding dependencies
and transformations automatically. 
See [additional configuration](#additional-configuration) if that needs tweaking.

```groovy
buildscript {
    ext.atomicfu_version = '0.14.3'

    dependencies {
        classpath "org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version"
    }
}

apply plugin: 'kotlinx-atomicfu'
```

### JS 

Configure add apply plugin just like for [JVM](#jvm). 

### Native

This library is available for Kotlin/Native (`atomicfu-native`).
Kotlin/Native uses Gradle metadata and needs Gradle version 5.3 or later.
See [Gradle Metadata 1.0 announcement](https://blog.gradle.org/gradle-metadata-1.0) for more details.
Apply the corresponding plugin just like for [JVM](#jvm). 

Atomic references for Kotlin/Native are based on 
[FreezableAtomicReference](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.concurrent/-freezable-atomic-reference/-init-.html)
and every reference that is stored to the previously 
[frozen](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.concurrent/freeze.html) 
(shared with another thread) atomic is automatically frozen, too.

Since Kotlin/Native does not generally provide binary compatibility between versions, 
you should use the same version of Kotlin compiler as was used to build AtomicFU. 
See [gradle.properties](gradle.properties) in AtomicFU project for its `kotlin_version`.

### Common

If you write a common code that should get compiled or different platforms, add `org.jetbrains.kotlinx:atomicfu-common`
to your common code dependencies or apply `kotlinx-atomicfu` plugin that adds this dependency automatically:

```groovy
dependencies {
    compile "org.jetbrains.kotlinx:atomicfu-common:$atomicfu_version"
}
```

### Additional configuration

There are the following additional parameters (with their defaults):

```groovy
atomicfu {
  dependenciesVersion = '0.14.3' // set to null to turn-off auto dependencies
  transformJvm = true // set to false to turn off JVM transformation
  transformJs = true // set to false to turn off JS transformation
  variant = "FU" // JVM transformation variant: FU,VH, or BOTH 
  verbose = false // set to true to be more verbose  
}
```

## Maven build setup

Declare AtomicFU version:

```xml
<properties>
     <atomicfu.version>0.14.3</atomicfu.version>
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
[VarHandle](https://docs.oracle.com/javase/9/docs/api/java/lang/invoke/VarHandle.html)
instead of `AtomicXxxFieldUpdater`. Configure transformation `variant` in Gradle build file:
 
```groovy
atomicfu {
    variant = "VH"
}
``` 
 
It can also create [JEP 238](https://openjdk.java.net/jeps/238) multi-release jar file with both
`AtomicXxxFieldUpdater` for JDK<=8 and `VarHandle` for for JDK9+ if you 
set `variant` to `"BOTH"`.

### Arrays of atomic values

You can declare arrays of all supported atomic value types. 
By default arrays are transformed into the corresponding `java.util.concurrent.atomic.Atomic*Array` instances.

If you configure `variant = "VH"` an array will be transformed to plain array using 
[VarHandle](https://docs.oracle.com/javase/9/docs/api/java/lang/invoke/VarHandle.html) to support atomic operations.
  
```kotlin
val a = atomicArrayOfNulls<T>(size) // similar to Array constructor

val x = a[i].value // read value
a[i].value = x // set value
a[i].compareAndSet(expect, update) // do atomic operations
```

### User-defined extensions on atomics

You can define you own extension functions on `AtomicXxx` types but they must be `inline` and they cannot
be public and be used outside of the module they are defined in. For example:

```kotlin
@Suppress("NOTHING_TO_INLINE")
private inline fun AtomicBoolean.tryAcquire(): Boolean = compareAndSet(false, true)
```

### Locks

This project includes `kotlinx.atomicfu.locks` package providing multiplatform locking primitives that
require no additional runtime dependencies on Kotlin/JVM and Kotlin/JS with a library implementation for 
Kotlin/Native.

* `SynchronizedObject` is designed for inheritance. You write `class MyClass : SynchronizedObject()` and then 
use `synchronized(instance) { ... }` extension function similarly to the 
[synchronized](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/synchronized.html)
function from the standard library that is available for JVM. The `SynchronizedObject` superclass gets erased
(transformed to `Any`) on JVM and JS, with `synchronized` leaving no trace in the code on JS and getting 
replaced with built-in monitors for locking on JVM.

* `ReentrantLock` is designed for delegation. You write `val lock = reentrantLock()` to construct its instance and
use `lock`/`tryLock`/`unlock` functions or `lock.withLock { ... }` extension function similarly to the way
[jucl.ReentrantLock](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html)
is used on JVM. On JVM it is a typealias to the later class, erased on JS.   

Condition variables (`notify`/`wait` and `signal`/`await`) are not supported.

### Testing lock-free data structures on JVM

You can optionally test lock-freedomness of lock-free data structures using
[`LockFreedomTestEnvironment`](atomicfu/src/jvmMain/kotlin/kotlinx/atomicfu/LockFreedomTestEnvironment.kt) class.
See example in [`LockFreeQueueLFTest`](atomicfu/src/jvmTest/kotlin/kotlinx/atomicfu/test/LockFreeQueueLFTest.kt).
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

For Gradle there is nothing else to add. Tests are always run using original (non-transformed) classes.
