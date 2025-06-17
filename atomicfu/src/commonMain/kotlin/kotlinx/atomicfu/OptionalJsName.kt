package kotlinx.atomicfu

/**
 * This annotation actualized with JsName in JS platform and not actualized in others.
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Deprecated("The annotation was intended for internal use only and will be hidden in the future release.")
public expect annotation class OptionalJsName(val name: String)
