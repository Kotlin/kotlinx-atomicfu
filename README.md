# AtomicFU 

[![Kotlin Beta](https://kotl.in/badges/beta.svg)](https://kotlinlang.org/docs/components-stability.html)
[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/atomicfu)](https://search.maven.org/artifact/org.jetbrains.kotlinx/atomicfu/0.23.1/pom)

>Note on Beta status: the plugin is in its active development phase and changes from release to release.
>We do provide a compatibility of atomicfu-transformed artifacts between releases, but we do not provide 
>strict compatibility guarantees on plugin API and its general stability between Kotlin versions.

**Atomicfu** is a multiplatform library that provides the idiomatic and efficient way of using atomic operations in Kotlin.

## Table of contents
- [Requirements](#requirements)
- [Features](#features)
- [Example](#example)
- [Quickstart](#quickstart)
  - [Apply plugin to a project](#apply-plugin)
    - [Gradle configuration](#gradle-configuration)
    - [Maven configuration](#maven-configuration)
- [Usage constraints](#usage-constraints)  
- [Transformation modes](#transformation-modes)
  - [Atomicfu compiler plugin](#atomicfu-compiler-plugin)
- [Options for post-compilation transformation](#options-for-post-compilation-transformation) 
  - [JVM options](#jvm-options)
  - [JS options](#js-options)
- [More features](#more-features)
  - [Arrays of atomic values](#arrays-of-atomic-values)
  - [User-defined extensions on atomics](#user-defined-extensions-on-atomics)
  - [Locks](#locks)
  - [Tracing operations](#tracing-operations)
- [Kotlin/Native support](#kotlin-native-support)

## Requirements

Starting from version `0.23.1` of the library your project is required to use:

* Gradle `7.0` or newer

* Kotlin `1.7.0` or newer

## Features

* Complete multiplatform support: JVM, Native, JS and Wasm (since Kotlin 1.9.20).
* Code it like a boxed value `atomic(0)`, but run it in production efficiently:
  * For **JVM**: an atomic value is represented as a plain value atomically updated with `java.util.concurrent.atomic.AtomicXxxFieldUpdater` from the Java standard library.
  * For **JS**: an atomic value is represented as a plain value.
  * For **Native**: atomic operations are delegated to Kotlin/Native atomic intrinsics.
  * For **Wasm**: an atomic value is not transformed, it remains boxed, and `kotlinx-atomicfu` library is used as a runtime dependency.
* Use Kotlin-specific extensions (e.g. inline `loop`, `update`, `updateAndGet` functions).
* Use atomic arrays, user-defined extensions on atomics and locks (see [more features](#more-features)).
* [Tracing operations](#tracing-operations) for debugging.
  
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

## Quickstart
### Apply plugin
#### Gradle configuration

Gradle configuration is supported for all platforms, minimal version is Gradle 6.8.

In top-level build file:

<details open>
<summary>Kotlin</summary>

```kotlin
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
      classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.23.1")
    }
}

apply(plugin = "kotlinx-atomicfu")
```
</details>

<details>
<summary>Groovy</summary>

```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.23.1'
    }
}
  
apply plugin: 'kotlinx-atomicfu'
```
</details>

#### Maven configuration

Maven configuration is supported for JVM projects.


<details open>
<summary>Declare atomicfu version</summary>

```xml
<properties>
     <atomicfu.version>0.23.1</atomicfu.version>
</properties> 
```

</details>

<details>
<summary>Declare provided dependency on the AtomicFU library</summary>

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

</details>

Configure build steps so that Kotlin compiler puts classes into a different `classes-pre-atomicfu` directory,
which is then transformed to a regular `classes` directory to be used later by tests and delivery.

<details>
<summary>Build steps</summary>

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
            <!-- "VH" to use Java 9 VarHandle, "BOTH" to produce multi-version code -->
            <variant>FU</variant>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

</details>

## Usage constraints

* Declare atomic variables as `private val` or `internal val`. You can use just (public) `val`, 
  but make sure they are not directly accessed outside of your Kotlin module (outside of the source set).
  Access to the atomic variable itself shall be encapsulated.
* To expose the value of an atomic property to the public, use a delegated property declared in the same scope
  (see [atomic delegates](#atomic-delegates) section for details):

```kotlin
private val _foo = atomic<T>(initial) // private atomic, convention is to name it with leading underscore
public var foo: T by _foo            // public delegated property (val/var)
```
* Only simple operations on atomic variables _directly_ are supported. 
  * Do not read references on atomic variables into local variables,
    e.g. `top.compareAndSet(...)` is ok, while `val tmp = top; tmp...` is not. 
  * Do not leak references on atomic variables in other way (return, pass as params, etc). 
* Do not introduce complex data flow in parameters to atomic variable operations, 
  i.e. `top.value = complex_expression` and `top.compareAndSet(cur, complex_expression)` are not supported 
  (more specifically, `complex_expression` should not have branches in its compiled representation).
  Extract `complex_expression` into a variable when needed.

## Atomicfu compiler plugin

To provide a user-friendly atomic API on the frontend and efficient usage of atomic values on the backend kotlinx-atomicfu library uses the compiler plugin to transform 
IR for all the target backends: 
* **JVM**: atomics are replaced with `java.util.concurrent.atomic.AtomicXxxFieldUpdater`.
* **Native**: atomics are implemented via atomic intrinsics on Kotlin/Native.
* **JS**: atomics are unboxed and represented as plain values.

To turn on IR transformation set these properties in your `gradle.properties` file:

<details open>
<summary>For Kotlin >= 1.7.20</summary>

```groovy
kotlinx.atomicfu.enableJvmIrTransformation=true // for JVM IR transformation
kotlinx.atomicfu.enableNativeIrTransformation=true // for Native IR transformation
kotlinx.atomicfu.enableJsIrTransformation=true // for JS IR transformation
```

</details>

<details>


<summary> For Kotlin >= 1.6.20 and Kotlin < 1.7.20</summary>

```groovy
kotlinx.atomicfu.enableIrTransformation=true // only JS IR transformation is supported
```

</details>

Also for JS backend make sure that `ir` or `both` compiler mode is set:

```groovy
kotlin.js.compiler=ir // or both
```


## Options for post-compilation transformation

Some configuration options are available for _post-compilation transform tasks_ on JVM and JS.

To set configuration options you should create `atomicfu` section in a `build.gradle` file, 
like this:
```groovy
atomicfu {
  dependenciesVersion = '0.23.1'
}
```

### JVM options

To turn off transformation for Kotlin/JVM set option `transformJvm` to `false`.

Configuration option `jvmVariant` defines the Java class that replaces atomics during bytecode transformation.
Here are the valid options:
- `FU` – atomics are replaced with [AtomicXxxFieldUpdater](https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/atomic/AtomicIntegerFieldUpdater.html).
- `VH` – atomics are replaced with [VarHandle](https://docs.oracle.com/javase/9/docs/api/java/lang/invoke/VarHandle.html), 
  this option is supported for JDK 9+.
- `BOTH` – [multi-release jar file](https://openjdk.java.net/jeps/238) will be created with both `AtomicXxxFieldUpdater` for JDK <= 8 and `VarHandle` for JDK 9+.

### JS options

To turn off transformation for Kotlin/JS set option `transformJs` to `false`.

Here are all available configuration options (with their defaults):
```groovy
atomicfu {
  dependenciesVersion = '0.23.1' // set to null to turn-off auto dependencies
  transformJvm = true // set to false to turn off JVM transformation
  jvmVariant = "FU" // JVM transformation variant: FU,VH, or BOTH
  transformJs = true // set to false to turn off JVM transformation
}
```

## More features

AtomicFU provides some additional features that you can use.

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

### Atomic delegates

You can expose the value of an atomic property to the public, using a delegated property 
declared in the same scope:

```kotlin
private val _foo = atomic<T>(initial) // private atomic, convention is to name it with leading underscore
public var foo: T by _foo            // public delegated property (val/var)
```

You can also delegate a property to the atomic factory invocation, that is equal to declaring a volatile property:  

```kotlin
public var foo: T by atomic(0)
```

This feature is only supported for the IR transformation mode, see the [atomicfu compiler plugin](#atomicfu-compiler-plugin) section for details.

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

> Note that package `kotlinx.atomicfu.locks` is experimental explicitly even while atomicfu is experimental itself,
> meaning that no ABI guarantees are provided whatsoever. API from this package is not recommended to use in libraries
> that other projects depend on.

### Tracing operations

You can debug your tests tracing atomic operations with a special trace object:

```kotlin
private val trace = Trace()
private val current = atomic(0, trace)

fun update(x: Int): Int {           
    // custom trace message
    trace { "calling update($x)" }
    // automatic tracing of modification operations 
    return current.getAndAdd(x)
}
```      

All trace messages are stored in a cyclic array inside `trace`. 
 
You can optionally set the size of trace's message array and format function. For example, 
you can add a current thread name to the traced messages:

```kotlin
private val trace = Trace(size = 64) {   
    index, // index of a trace message 
    text   // text passed when invoking trace { text }
    -> "$index: [${Thread.currentThread().name}] $text" 
} 
```                           

`trace` is only seen before transformation and completely erased after on Kotlin/JVM and Kotlin/JS.

## Kotlin Native support

Atomic references for Kotlin/Native are based on
[FreezableAtomicReference](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.concurrent/-freezable-atomic-reference/-init-.html)
and every reference that is stored to the previously
[frozen](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.concurrent/freeze.html)
(shared with another thread) atomic is automatically frozen, too.

Since Kotlin/Native does not generally provide binary compatibility between versions,
you should use the same version of Kotlin compiler as was used to build AtomicFU.
See [gradle.properties](gradle.properties) in AtomicFU project for its `kotlin_version`.

Available Kotlin/Native targets are based on non-deprecated official targets [Tier list](https://kotlinlang.org/docs/native-target-support.html)
 with the corresponding compatibility guarantees.
