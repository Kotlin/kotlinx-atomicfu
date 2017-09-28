# Change log for kotlinx.atomicfu 

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
