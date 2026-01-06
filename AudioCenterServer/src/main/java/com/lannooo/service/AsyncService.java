package com.lannooo.service;

import com.lannooo.common.Utils;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

@Component
public class AsyncService {
    private static final Logger logger = Utils.getLogger(AsyncService.class);

    private final ExecutorService executor;

    public AsyncService(ExecutorService executor) {
        this.executor = executor;
    }

    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }
}
