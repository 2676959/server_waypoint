package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LoggerFactoryBinder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Captures actual SLF4J output without adding a runtime logging backend to the library. */
public final class StaticLoggerBinder implements LoggerFactoryBinder {
    public static String REQUESTED_API_VERSION = "1.7.30";
    private static final StaticLoggerBinder INSTANCE = new StaticLoggerBinder();
    private static final ThreadLocal<List<String>> EVENTS = ThreadLocal.withInitial(ArrayList::new);

    public static StaticLoggerBinder getSingleton() { return INSTANCE; }
    public static List<String> events() { return List.copyOf(EVENTS.get()); }
    public static void clear() { EVENTS.get().clear(); }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return name -> (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getReturnType() == boolean.class) return true;
                    if (args != null && args.length > 0 && args[0] instanceof String pattern) {
                        Object[] values = args.length == 2 && args[1] instanceof Object[] array
                                ? array : Arrays.copyOfRange(args, 1, args.length);
                        EVENTS.get().add(name + " " + method.getName() + " "
                                + MessageFormatter.arrayFormat(pattern, values).getMessage());
                    }
                    return null;
                });
    }

    @Override
    public String getLoggerFactoryClassStr() { return getClass().getName(); }
}
