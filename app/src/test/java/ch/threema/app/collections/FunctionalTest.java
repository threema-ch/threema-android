package ch.threema.app.collections;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;

public class FunctionalTest {

    @Test
    public void filterNonNull() {
        final List<String> names = new ArrayList<>();
        names.add("MC Hammer");
        names.add("Knöppel");
        names.add(null);
        names.add("Nella Martinetti");

        final List<String> loud = Functional.filter(names, new IPredicateNonNull<String>() {
            @Override
            public boolean apply(@NonNull String value) {
                return value.equals("Knöppel");
            }
        });

        assertEquals(2, loud.size());
        assertEquals("Knöppel", loud.get(0));
        assertEquals(null, loud.get(1));
    }

}
