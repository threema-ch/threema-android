package ch.threema.app;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark tests that may cause data loss in the Threema database,
 * file system or settings (e.g. when deleting the identity to test certain aspects of the app).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DangerousTest {
    String reason() default "";
}
