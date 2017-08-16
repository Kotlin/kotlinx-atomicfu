# Change log for kotlinx.atomicfu 

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
  (other pontential uses via interceptors in the future).

## Version 0.1

* Initial release.
