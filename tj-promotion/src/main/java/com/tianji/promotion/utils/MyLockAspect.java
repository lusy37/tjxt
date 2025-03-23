package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Aspect
@RequiredArgsConstructor
@Slf4j
public class MyLockAspect implements Ordered {

    private final RedissonClient redissonClient;
    private final MyLockFactory lockFactory;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint joinPoint, MyLock myLock) throws Throwable {

        // 基于 SPEL 表达式获取锁名称
        String name = getLockName(myLock.name(), joinPoint);
        // 创建锁对象
        RLock lock = lockFactory.getLock(myLock.lockType(), name);
        // RLock lock = redissonClient.getLock(myLock.name());
        // 尝试获取锁
        boolean isLock = myLock.lockStrategy().tryLock(lock, myLock);
        // boolean isLock = lock.tryLock(myLock.waitTime(), myLock.leaseTime(), myLock.unit());
        // 判断是否成功
        if (!isLock){
            // 失败, 快速结束
            throw new BizIllegalException("请求太频繁");
        }
        try {
            // 成功, 执行业务逻辑
            return joinPoint.proceed();
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * SPEL的正则规则
     */
    private static final Pattern pattern = Pattern.compile("\\#\\{([^\\}]*)\\}");
    /**
     * 方法参数解析器
     */
    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析锁名称
     * @param name 原始锁名称
     * @param pjp 切入点
     * @return 解析后的锁名称
     */
    /*private String getLockName(String name, ProceedingJoinPoint pjp) {
        // 1.判断是否存在spel表达式
        if (StringUtils.isBlank(name) || !name.contains("#")) {
            // 不存在，直接返回
            return name;
        }
        // 2.构建context
        EvaluationContext context = new MethodBasedEvaluationContext(
                TypedValue.NULL, resolveMethod(pjp), pjp.getArgs(), parameterNameDiscoverer);
        // 3.构建解析器
        ExpressionParser parser = new SpelExpressionParser();
        // 3.循环处理
        Matcher matcher = pattern.matcher(name);
        while (matcher.find()) {
            // 2.1.获取表达式
            String tmp = matcher.group();
            // 2.2.尝试解析
            Expression expression = parser.parseExpression("#" + matcher.group(1));
            Object value = expression.getValue(context);
            name = name.replace(tmp, ObjectUtils.nullSafeToString(value));
        }
        return name;
    }*/

    private String getLockName(String name, ProceedingJoinPoint pjp) {
        if (StringUtils.isBlank(name) || !name.contains("#")) {
            log.info(" 无需解析 SpEL，锁名称直接返回: {}", name);
            return name;
        }

        log.info(" 开始解析 SpEL 锁名称: {}", name);

        // 1. 使用 StandardEvaluationContext（支持 T(...) 静态方法）
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("T", new StandardTypeLocator()); // 允许解析 T()

        // 2. 解析方法参数（兼容 #{code} 变量）
        Method method = resolveMethod(pjp);
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = pjp.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]); // 把方法参数放入上下文
                log.info("🔹 解析方法参数: {} = {}", paramNames[i], args[i]);
            }
        }

        // 3. 正则匹配 `#{}` 变量并解析
        Matcher matcher = pattern.matcher(name);
        while (matcher.find()) {
            String tmp = matcher.group(); // #{code} 或 #{T(...)}
            String spelExpression = matcher.group(1); // 提取 `code` 或 `T(...)`
            log.info("发现 SpEL 表达式: {}", spelExpression);

            Expression expression = new SpelExpressionParser().parseExpression(spelExpression);
            Object value = expression.getValue(context);

            log.info("SpEL 解析结果: {} -> {}", tmp, value);
            name = name.replace(tmp, ObjectUtils.nullSafeToString(value));
        }

        log.info("最终锁名称: {}", name);
        return name;

    }

    private Method resolveMethod(ProceedingJoinPoint pjp) {
        // 1.获取方法签名
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        // 2.获取字节码
        Class<?> clazz = pjp.getTarget().getClass();
        // 3.方法名称
        String name = signature.getName();
        // 4.方法参数列表
        Class<?>[] parameterTypes = signature.getMethod().getParameterTypes();
        return tryGetDeclaredMethod(clazz, name, parameterTypes);
    }

    private Method tryGetDeclaredMethod(Class<?> clazz, String name, Class<?> ... parameterTypes){
        try{
            // 5.反射获取方法
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                // 尝试从父类寻找
                return tryGetDeclaredMethod(superClass, name, parameterTypes);
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
