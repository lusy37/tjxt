package com.tianji.promotion.utils;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface MyLock {

    String name();

    long waitTime() default 1;

    long leaseTime() default -1;

    TimeUnit unit() default TimeUnit.SECONDS;

    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK;

    MyLockStrategy lockStrategy() default MyLockStrategy.FAIL_FAST;
}
