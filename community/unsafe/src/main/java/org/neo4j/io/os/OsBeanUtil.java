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
package org.neo4j.io.os;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

/**
 * Utility class that exposes methods from proprietary implementations of {@link OperatingSystemMXBean}.
 * Methods never fail but instead return {@link #VALUE_UNAVAILABLE} if such method is not exposed by the underlying
 * MX bean.
 */
public final class OsBeanUtil {
    public static final long VALUE_UNAVAILABLE = -1;

    private static final String OPENJDK_OS_BEAN = "com.sun.management.OperatingSystemMXBean";
    private static final String OPENJDK_UNIX_OS_BEAN = "com.sun.management.UnixOperatingSystemMXBean";

    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    private static final Method getTotalMemoryMethod;
    private static final Method getFreeMemoryMethod;
    private static final Method getCommittedVirtualMemoryMethod;
    private static final Method getTotalSwapSpaceMethod;
    private static final Method getFreeSwapSpaceMethod;
    private static final Method getMaxFileDescriptorsMethod;
    private static final Method getOpenFileDescriptorsMethod;
    private static final Method getCpuLoad;
    private static final Method getProcessCpuLoad;

    static {
        getTotalMemoryMethod = findOsBeanMethod("getTotalMemorySize");
        getFreeMemoryMethod = findOsBeanMethod("getFreeMemorySize");
        getCommittedVirtualMemoryMethod = findOsBeanMethod("getCommittedVirtualMemorySize");
        getTotalSwapSpaceMethod = findOsBeanMethod("getTotalSwapSpaceSize");
        getFreeSwapSpaceMethod = findOsBeanMethod("getFreeSwapSpaceSize");
        getMaxFileDescriptorsMethod = findUnixOsBeanMethod("getMaxFileDescriptorCount");
        getOpenFileDescriptorsMethod = findUnixOsBeanMethod("getOpenFileDescriptorCount");
        getCpuLoad = findOsBeanMethod("getCpuLoad");
        getProcessCpuLoad = findOsBeanMethod("getProcessCpuLoad");
    }

    private OsBeanUtil() {
        throw new AssertionError("Not for instantiation!");
    }

    /**
     * @return total amount of memory in bytes, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static long getTotalMemory() {
        return invokeToLong(getTotalMemoryMethod);
    }

    /**
     * @return amount of free memory in bytes, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static long getFreeMemory() {
        return invokeToLong(getFreeMemoryMethod);
    }

    /**
     * @return amount of virtual memory that is guaranteed to be available to the running process in bytes, or
     * {@link #VALUE_UNAVAILABLE} if underlying bean does not provide this functionality.
     */
    public static long getCommittedVirtualMemory() {
        return invokeToLong(getCommittedVirtualMemoryMethod);
    }

    /**
     * @return total amount of swap space in bytes, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static long getTotalSwapSpace() {
        return invokeToLong(getTotalSwapSpaceMethod);
    }

    /**
     * @return total amount of free swap space in bytes, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static long getFreeSwapSpace() {
        return invokeToLong(getFreeSwapSpaceMethod);
    }

    /**
     * @return maximum number of file descriptors, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static long getMaxFileDescriptors() {
        return invokeToLong(getMaxFileDescriptorsMethod);
    }

    /**
     * @return number of open file descriptors, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static long getOpenFileDescriptors() {
        return invokeToLong(getOpenFileDescriptorsMethod);
    }

    /**
     * @return cpu load, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static double getCpuLoad() {
        return invokeToDouble(getCpuLoad);
    }

    /**
     * @return process cpu load, or {@link #VALUE_UNAVAILABLE} if underlying bean does not
     * provide this functionality.
     */
    public static double getProcessCpuLoad() {
        return invokeToDouble(getProcessCpuLoad);
    }

    private static Method findUnixOsBeanMethod(String methodName) {
        return findMethod(OPENJDK_UNIX_OS_BEAN, methodName);
    }

    private static Method findOsBeanMethod(String methodName) {
        return findMethod(OPENJDK_OS_BEAN, methodName);
    }

    private static Method findMethod(String className, String methodName) {
        try {
            return (methodName == null) ? null : Class.forName(className).getMethod(methodName);
        } catch (Throwable t) {
            return null;
        }
    }

    private static long invokeToLong(Method method) {
        try {
            Object value = (method == null) ? null : method.invoke(osBean);
            return (value == null) ? VALUE_UNAVAILABLE : ((Number) value).longValue();
        } catch (Throwable t) {
            return VALUE_UNAVAILABLE;
        }
    }

    private static double invokeToDouble(Method method) {
        try {
            Object value = (method == null) ? null : method.invoke(osBean);
            return (value == null) ? VALUE_UNAVAILABLE : ((Number) value).doubleValue();
        } catch (Throwable t) {
            return VALUE_UNAVAILABLE;
        }
    }
}
