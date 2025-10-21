package org.neo4j.fleetmanagement.bootstrap;

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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.ServiceLoader;

public class Reflection {
    public static <S> S getNewInstanceOfImplementationOf(Class<S> service, Object... initargs)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException,
                    InvocationTargetException {
        var argsTypes = Arrays.stream(initargs).map(Object::getClass).toArray(Class[]::new);
        return getConstructorOfImplementationOf(service, argsTypes).newInstance(initargs);
    }

    public static <S> Constructor<S> getConstructorOfImplementationOf(Class<S> service, Class... argsTypes)
            throws ClassNotFoundException, NoSuchMethodException {
        var loader = ServiceLoader.load(service);
        for (Object implClass : loader) {
            return (Constructor<S>) implClass.getClass().getDeclaredConstructor(argsTypes);
        }
        throw new ClassNotFoundException("No implementation found for " + service.getName());
    }
}
