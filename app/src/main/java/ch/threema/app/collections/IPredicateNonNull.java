package ch.threema.app.collections;

import androidx.annotation.NonNull;

public interface IPredicateNonNull<T> {
    boolean apply(@NonNull T value);
}
