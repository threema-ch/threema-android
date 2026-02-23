package ch.threema.base.utils;

import java.net.InetAddress;
import java.util.concurrent.*;

import androidx.annotation.NonNull;

public class AsyncResolver implements Callable<InetAddress[]> {
    private final String host;

    public AsyncResolver(String host) {
        this.host = host;
    }

    @Override
    public InetAddress[] call() throws Exception {
        return InetAddress.getAllByName(host);
    }

    @NonNull
    public static InetAddress[] getAllByName(String host) throws ExecutionException, InterruptedException {
        AsyncResolver resolver = new AsyncResolver(host);
        ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
        Future<InetAddress[]> future = executorService.submit(resolver);
        return future.get();
    }
}
