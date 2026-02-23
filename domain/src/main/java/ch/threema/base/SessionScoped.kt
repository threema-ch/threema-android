package ch.threema.base

/**
 * Can be used to mark classes which (directly or indirectly) depend on the master key being unlocked.
 * When using dependency injection with such classes, it is the responsibility of the caller to ensure that it is safe to do so,
 * e.g., by checking whether the session scope is currently active.
 *
 * Note that no automatic checks are performed for this annotation, it exists purely for the purpose of documentation.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
annotation class SessionScoped
