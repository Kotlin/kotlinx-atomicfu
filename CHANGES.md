# Change log for kotlinx.atomicfu

# Version 0.23.1

* Updated Kotlin to 1.9.21 (#361).
* Update to Kotlin 1.9.21 fixes regression with klib incompatibility (#365).

# Version 0.23.0

* Updated Kotlin to 1.9.20 (#361).
* Updated Gradle version to 8.3.
* Supported transformations for Native targets 🎉 (#363) .
* Introduced WebAssembly target (`wasmJs` and `wasmWasi`) 🎉 (#334).
* Improved integration testing for `atomicfu-gradle-plugin` (#345).
* Updated implementation of native atomics (#336).
* Got rid of `previous-compilation-data.bin` file in META-INF (#344).

# Version 0.22.0

* Updated Kotlin to 1.9.0 (#330).
* Updated gradle version to 8.1 (#319).
* Updated kotlinx.metadata version 0.7.0 (#327).
* Conditionally removed targets that are removed after 1.9.20 (iosArm32, watchosX86). (#320).
* Removed obsolete no longer supported kotlin.mpp.enableCompatibilityMetadataVariant (#326).
* Complied with new compiler restriction on actual declaration annotations (#325).

# Version 0.21.0

* Updated Kotlin to 1.8.20.
* Updated Gradle to 7.3 (#300).
* Updated kotlinx.metadata version to 0.6.0 (#281).
* Minimal supported KGP(1.7.0) and Gradle(7.0) versions are set since this release.
* Removed JS Legacy configurations for KGP >= 1.9.0 (#296).
* Fixed class duplication (from original and transformed directories) in Jar (#301).
* Original class directories are not modified in case of compiler plugin application (#312).

# Version 0.20.2

* Fix for unresolved `kotlinx-atomicfu-runtime` dependency error (https://youtrack.jetbrains.com/issue/KT-57235),
please see the corresponding PR for more comments (#290).

# Version 0.20.1

* Fixed passing `kotlinx-atomicfu-runtime` dependency to the runtime classpath (#283).
* AV/LV set to 1.4 to be compatible with Gradle 7 (#287).
* Enable cinterop commonization (#282).

# Version 0.20.0

* Update Kotlin to 1.8.10.
* Support all official K/N targets (#275).

# Version 0.19.0

* Update Kotlin to 1.8.0.
* Update LV to 1.8 (#270).
* Prepare atomicfu for including to the Kotlin Aggregate build (#265).

# Version 0.18.5

* Support JVM IR compiler plugin (#246).
* Update Kotlin to 1.7.20. 
* Added more tests for atomicfu-gradle-plugin (#255).

# Version 0.18.4

* Fix KGP compatibility bug with freeCompilerArgs modification (#247).
* Update kotlinx.metadata to 0.5.0 (#245). 
* Update gradle version to 6.8.3 (#244)

# Version 0.18.3

* Fix for atomicfu-gradle-plugin application to the MPP project (for Kotlin 1.7.20).

# Version 0.18.2

* In Kotlin 1.7.10 the name of `atomicfu-runtime` module was reverted back to `kotlinx-atomicfu-runtime`, 
  as the renaming was an incompatible change. 
  Fixed `atomicfu-gradle-plugin` to add `kotlinx-atomicfu-runtime` dependency directly.

# Version 0.18.1

* Fix for the compatibility issue: add `atomicfu-runtime` dependency directly since Kotlin 1.7.10.

# Version 0.18.0

* Update Kotlin to 1.7.0.
* Fix kotlin 1.7 compatibility (#222).
* Update JVM target to 1.8 (see KT-45165).
* Fix for parsing Kotlin version in AtomicfuGradlePlugin.

# Version 0.17.3

* Adding compiler plugin dependency only for projects with KGP >= 1.6.20 (#226).
* Compiler plugin runtime dependency fixes (#230).
* Update README badges (#228).

# Version 0.17.2

* Update Kotlin to 1.6.20.
* IR transformation for Kotlin/JS. (#215).
* Update ASM to 9.3 for Java 18 support (#223)
* Update kotlinx.metadata to 0.4.2.

# Version 0.17.1

* Support of `org.jetbrains.kotlin.js` plugin (#218).
* Fixed configuration cache bug. (#216).
* Bug fixes for delegated fields support (#179).

# Version 0.17.0

* Update Kotlin to 1.6.0.
* Update ASM minimal api version to ASM7 (#203).
* Add explicit module-info for JPMS compatibility (#201).

# Version 0.16.3

* Kotlin is updated to 1.5.30.
* All references to Bintray are removed from artefacts POMs.
* Added new Apple Silicon targets for K/N.

# Version 0.16.2

* Update Kotlin to 1.5.20.
* ASM 9.1 for Java 15+ support (#190).
* Removing extra atomicfu references from LVT.

# Version 0.16.0

* Update Kotlin to 1.5.0.
* Supported x86_64-based watchOS simulator target. (#177).

# Version 0.15.2

* Update kotlinx-metadata to 0.2.0.
* Update Kotlin to 1.4.30.
* Added kotlin space repository.

# Version 0.15.1

* Maven central publication (#173).
* Binary compatibility with IR (#170).
* Supported garbage-free multi-append in debug trace (#172).

# Version 0.15.0

* Tracing atomic operations (#20).
* Supported delegated properties (#83).
* Fixed visibility modifiers of synthetic fields and classes (#144).
* Introduced `size` method for atomic arrays (#149).
* Update Kotlin to 1.4.10.

# Version 0.14.4

* Fixed bug when Maven plugin wasn't published
* Migrate to new Kotlin HMPP metadata for multiplatform projects 
* Update Kotlin to 1.4.0

# Version 0.14.3

* Update to Kotlin 1.3.71.
* Enable HMPP and new JS IR backend compilation.

# Version 0.14.2

* Update to Kotlin 1.3.70.

# Version 0.14.1

* Fixed broken JVM transformer after upgrade to ASM 7.2.

# Version 0.14.0

* Updated to Kotlin 1.3.60.
* Updated to ASM 7.2.
* Support locks (SynchronizedObject and ReentrantLock).
* Freezable atomics on Kotlin/Native.

# Version 0.13.2

* Added release notes.
* Added the original classesDirs to the friend paths of test compilations.

# Version 0.13.1

* Better diagnostics when LockFreedomTestEnvironment fails to shutdown.
* Fixed looking for local variables scope labels.

# Version 0.13.0

* Gradle version 5.6.1 with Gradle metadata format version 1.0 (stable) for native modules.
* Optimized volatile-only fields in JVM.
* Supported unchecked cast erasure (including array elements).
* Fixed inline functions on array elements.
* Fixed shutdown sequence of LockFreedomTestEnvironment.

# Version 0.12.11

* Support suspending functions in LockFreedomTestEnvironment.

# Version 0.12.10

* Updated to Kotlin 1.3.50

# Version 0.12.9

* Updated to Kotlin 1.3.40

# Version 0.12.8

* Fixed getting array elements by named index.
* Fixed broken npm publishing.

# Version 0.12.7

* Fixed BooleanArray setValue.
* Fixed removal of inline methods on atomicfu types from bytecode.
* Adjust kotlin.Metadata in JVM classes to remove atomicfu references completely. 

# Version 0.12.6

* Support additional configuration for dependencies and transforms.
* Get array field fixed (see #61).

# Version 0.12.5

* Fixed Gradle plugin compatibility with `kotlin-multiplatform` plugin.

# Version 0.12.4

* Gradle plugin automatically adds dependencies. 
* Added support for inline extensions on `AtomicXxx` types.

# Version 0.12.3

* Updated to Kotlin 1.3.30

# Version 0.12.2

* Fixed to skip changing source path for unprocessed native output (see #51).
* Fixed inlining of atomic operations on JS (see #52).

# Version 0.12.1

* Gradle 4.10 with metadata version 0.4.
* No metadata for everything except native.

# Version 0.12.0

* Kotlin version 1.3.11.
* Support top-level atomic variables.
* Support arrays of atomic variables.
* Project is built with kotlin-multiplatform plugin.

# Version 0.11.11

* Kotlin version Kotlin version 1.3.0-rc-146 (with K/N).
* Gradle plugin supports projects that use `kotlin-multiplatform` plugin.
* Disable Gradle metadata publishing for all but native modules.
* JS transformer is more robust and retains line numbers (see #25).

# Version 0.11.10

* Kotlin version 1.3.0-rc-57 & Kotlin/Native 0.9.2

# Version 0.11.9

* Kotlin/Native version 1.3.0-rc-116 (0.9.3)
* Kotlin version 1.2.71
* Incremental JS compilation is fixed in plugin

## Version 0.11.7

* Fixed non-transformed AtomicBoolean and its tests
* AtomicFUGradlePlugin: More consistent task naming & code refactoring

## Version 0.11.6

* Kotlin/Native version 0.9

## Version 0.11.5

* Gradle plugin for JS: Fixed paths on Windows

## Version 0.11.4

* JS: Transformer added. It is now a compile-only dependency just like on JVM.   
* JVM: Default transformation variant is changed back to JDK6-compatible "FU".
  * `atomicfu { variant = xxx }` configuration section in Gradle can be used to change it.

## Version 0.11.3

* Fixed lost files during class analysis phase.

## Version 0.11.2

* Kotlin version 1.2.61
* Kotlin/Native version 0.8.2
* More user-friendly Gradle plugin for Kotlin/JVM and multi-release jar by default. See updated section in [README.md](README.md#Gradle)
* Supports `internal` atomic variables that are accessed from a different package in the same module.  

## Version 0.11.1

* Kotlin version 1.2.60
* Kotlin/Native version 0.8.1

## Version 0.11.0

* AtomicBoolean support (see #6)
* Kotlin/Native 0.9-dev-2922, all platforms, published to Maven Central

## Version 0.10.3-native

* Kotlin 1.2.51.
* Initial Kotlin/Native support:
  * Build for Kotlin/Native 0.8.
  * Only JS-like single-threaded applications are supported (no actual atomics).
  * Supported targets: "ios_arm64", "ios_arm32", "ios_x64", "macos_x64".
* NOTE: This version is not available in NPM and Maven Central. Use this Bintray repository: 
  * `maven { url "https://kotlin.bintray.com/kotlinx" }`    

## Version 0.10.3

* Kotlin 1.2.50.

## Version 0.10.2

* JS: Main file renamed to kotlinx-atomicfu.js to match NPM module name.

## Version 0.10.1

* JS: NPM deployment.

## Version 0.10.0

* Kotlin 1.2.41.
* Multiplatform: 
  * Extracted common code into `atomicfu-common` module.
  * Basic support on JS via `atomicfu-js` module (boxed objects, Bintray publishing only).
* JVM transformer:  
  * Preserve annotations on atomic fields.
  * Ignore no-ops in flow analyzer (support more variety of code patterns).

## Version 0.9.2

* Replaced deprecated kotlin-stdlib-jre8 dependency with kotlin-stdlib-jdk8.

## Version 0.9.1

* Kotlin 1.2.0

## Version 0.9

* Support generation of `VarHandle` variant for Java 9.

## Version 0.8

* `atomicfu-gradle-plugin` introduced.

## Version 0.7

* Fixed lost ACC_STATIC on <clinit> methods.
* Publish to Maven Central. 

## Version 0.6

* toString defined for debugging.

## Version 0.5

* Longer timeout to detect stalls in lock-free code, with shutdown logic
  that detected them even on short runs.
* Kotlin 1.1.4  

## Version 0.4

* Publish sources.
* Provide top-level `pauseLockFreeOp` for debugging.
* Stability improvements.

## Version 0.3

* Improved handling of compiler local variables for atomic fields.
* Support atomicVar.value = constant (with LDC instruction).
* Provide randomSpinWaitIntermission for lock-freedom tests.

## Version 0.2

* Support non-private atomic fields in nested classes that are accessed by other
  classes in the same compilation unit.
* Support for lock-freedom testing on unprocessed code 
  (other potential uses via interceptors in the future).

## Version 0.1

* Initial release.
