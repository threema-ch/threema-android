package ch.threema.annotation

/**
 * Denotes that the annotated method or class can only be safely called from a
 * single thread (e.g. it is NOT "thread safe").
 *
 * If it is a class, all of its method must be called from the same thread,
 * except where other thread annotations have been used. If you require
 * calling from multiple threads, synchronise on the instance object.
 *
 * If it is a method, the above description applies to the method.
 *
 * Note: This annotation is not checked by the IDE! It is meant as a helper
 * to annotate classes consistently in addition to UiThread and co.
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.CLASS,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class SameThread
