/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.common;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Utility that provides customisable handling options for cases when multiple threads attempt to access an object's
 * methods concurrently. This works by creating a dynamic proxy around the object.
 *
 * @see ThreadSanitizer#sanitize
 */
public class ThreadSanitizer {

    /**
     * Wrap given object in proxy that handles cases threads attempting to access the objects methods concurrently.
     * <p>
     * <b>Note:</b> all methods to be sanitized must be known to an interface that the class of the given object
     * inherits from. Sanitization will not occur for methods to which this does not apply.
     *
     * @param toSanitize the object to wrap.
     * @param toSanitizeType the class type of an interface implemented by {@code toSanitize}, also the intended return
     *                       type. Note that this interface must have visibility of the methods that need sanitizing.
     * @param exceptionHandler the logic that should execute on concurrent access.
     * @return the proxy for {@code toSanitize}, which will be of the type described by {@code toSanitizeType}.
     * @param <T> the interface described by {@code toSanitizeType}.
     */
    public static <T> T sanitize(T toSanitize, Class<T> toSanitizeType, Consumer<Exception> exceptionHandler) {
        Class<T> aClass = (Class<T>) toSanitize.getClass();
        return toSanitizeType.cast(Proxy.newProxyInstance(
                aClass.getClassLoader(),
                aClass.getInterfaces(),
                new ProxyInvocationHandler<>(toSanitize, exceptionHandler)));
    }

    private static class ProxyInvocationHandler<T> implements InvocationHandler {
        private final T toSanitize;
        private final Consumer<Exception> exceptionHandler;
        private final AtomicReference<RuntimeException> accessor = new AtomicReference<>();

        public ProxyInvocationHandler(T toSanitize, Consumer<Exception> exceptionHandler) {
            this.toSanitize = toSanitize;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            RuntimeException thisAccessorException =
                    new RuntimeException("Concurrent access of thread sanitized method detected.");
            RuntimeException previousAccessorException = accessor.compareAndExchange(null, thisAccessorException);
            if (previousAccessorException != null) {
                exceptionHandler.accept(previousAccessorException);
                exceptionHandler.accept(thisAccessorException);
            }

            try {
                return method.invoke(toSanitize, args);
            } finally {
                accessor.set(null);
            }
        }
    }
}
