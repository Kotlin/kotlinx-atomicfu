package org.example;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class JavaClass {
    private static final AtomicLongFieldUpdater<JavaClass> updater = AtomicLongFieldUpdater.newUpdater(
            JavaClass.class, "cnt"
    );

    private volatile long cnt = 0L;

    public long incAndGet() {
        return updater.incrementAndGet(this);
    }
}
