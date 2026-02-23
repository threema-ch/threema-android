package ch.threema.app;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnitRunner;

public class ThreemaTestRunner extends AndroidJUnitRunner {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(cl, ThreemaApplication.class.getName(), context);
    }
}
