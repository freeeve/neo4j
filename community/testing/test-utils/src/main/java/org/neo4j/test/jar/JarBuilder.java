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
package org.neo4j.test.jar;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.attribute.MethodAttributeAppender;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.SimpleRemapper;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Utility to create jar files containing classes from the current classpath.
 */
public final class JarBuilder {
    public static byte[] classCompiledBytes(String fileName) throws IOException {
        try (InputStream in = JarBuilder.class.getClassLoader().getResourceAsStream(fileName)) {
            requireNonNull(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (in.available() > 0) {
                out.write(in.read());
            }

            return out.toByteArray();
        }
    }

    public static List<String> createJarFor(Path pth, Class<?>... classes) {
        assert classes.length > 0;
        var file = pth.toFile();
        var remappedClassNames = new ArrayList<String>(classes.length);
        try {
            try (var cls = subclass(classes[0])) {
                cls.toJar(file);
                remappedClassNames.add(cls.getTypeDescription().getName());
            }
            for (int i = 1; i < classes.length; i++) {
                try (var cls = subclass(classes[i])) {
                    cls.inject(file);
                    remappedClassNames.add(cls.getTypeDescription().getName());
                }
            }
        } catch (IOException exc) {
            throw new RuntimeException("Could not write %s to %s.".formatted(Arrays.toString(classes), pth), exc);
        }
        return remappedClassNames;
    }

    private static DynamicType.Unloaded<?> subclass(Class<?> cls) {
        // To avoid that the classes we attempt to load are already class-loaded by the application classloader when
        // we refer to them by name, we subclass them and provide a new unloaded class with the same methods, and
        // annotations.
        return new ByteBuddy()
                .subclass(cls)
                .method(ElementMatchers.isDeclaredBy(cls))
                .intercept( // Proxy all method calls declared by the original class to the original class
                        SuperMethodCall.INSTANCE)
                .attribute( // Instrument the methods with the annotations of the original class
                        MethodAttributeAppender.ForInstrumentedMethod.INCLUDING_RECEIVER)
                .make();
    }

    private static String toInternalIdentifier(String className) {
        return className.replace('.', '/');
    }

    private static String fromInternalIdentifier(String className) {
        return className.replace('/', '.');
    }

    private static String convertToJarEntryPath(String className) {
        return toInternalIdentifier(className) + ".class";
    }

    private static HashMap<String, String> generateNameMappings(List<Class<?>> classes, String newSuffix) {
        var mappings = new HashMap<String, String>();
        for (Class<?> clazz : classes) {
            var fqn = toInternalIdentifier(clazz.getName());
            var packageName = fqn.substring(0, fqn.lastIndexOf('/'));
            var className = fqn.substring(fqn.lastIndexOf('/') + 1);
            var innerClasses = className.split("\\$");
            var updatedName = new StringBuilder();
            updatedName.append(packageName).append("/");
            for (int j = 0; j < innerClasses.length; ++j) {
                updatedName.append(innerClasses[j]).append(newSuffix);
                if (j != innerClasses.length - 1) {
                    updatedName.append("$");
                }
            }
            var newFQN = updatedName.toString();
            mappings.put(fqn, newFQN);
        }
        return mappings;
    }

    private static byte[] rewriteClass(Class<?> clazz, HashMap<String, String> mappings) throws IOException {
        var originalClassEntryPath = convertToJarEntryPath(clazz.getName());
        var classByteStream = clazz.getClassLoader().getResourceAsStream(originalClassEntryPath);
        if (classByteStream == null) {
            throw new IOException("Could not find class resource " + originalClassEntryPath);
        }
        var classReader = new ClassReader(classByteStream);
        var classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        var remapper = new ClassRemapper(classWriter, new SimpleRemapper(mappings));
        classReader.accept(remapper, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }

    // The ByteBuddy approach in createJarFor above works by creating a small wrapper subclass that invokes the
    // original's methods. This is fine for most things and avoids other access restrictions, but it doesn't
    // update cross-references between classes.
    // To aid tests loading a set of inter-related classes this method fully renames all the classes and their
    // internal references.
    // For Inner classes the parent must be included in the includedClasses
    // includedClasses are renamed and put into the output jar.
    // References to renamedButNotIncludedClasses are modified, but they are not included
    // in the output jar
    public static List<String> createJarWithRenamedClassesFor(
            Path pth, List<Class<?>> includedClasses, List<Class<?>> renamedButNotIncludedClasses) {
        assert !includedClasses.isEmpty();
        var newSuffix = Long.toString(Instant.now().getEpochSecond());
        var allClasses = new ArrayList<>(includedClasses);
        allClasses.addAll(renamedButNotIncludedClasses);
        var mappings = generateNameMappings(allClasses, newSuffix);
        var remappedClassNames = new ArrayList<String>(allClasses.size());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (var fs = new FileOutputStream(pth.toFile(), false);
                var jarStream = new JarOutputStream(fs, manifest)) {
            for (Class<?> clazz : includedClasses) {
                var mappedName = mappings.get(toInternalIdentifier(clazz.getName()));
                remappedClassNames.add(fromInternalIdentifier(mappedName));
                var mappedClassEntryPath = convertToJarEntryPath(mappedName);
                JarEntry entry = new JarEntry(mappedClassEntryPath);
                entry.setTime(Instant.now().toEpochMilli());
                jarStream.putNextEntry(entry);
                var rewrittenClassBytes = rewriteClass(clazz, mappings);
                jarStream.write(rewrittenClassBytes);
                jarStream.closeEntry();
            }
        } catch (IOException exc) {
            throw new RuntimeException("Could not write %s to %s.".formatted(allClasses, pth), exc);
        }
        return remappedClassNames;
    }

    // Renames all the classes including their cross-references
    // and puts them into a jar
    public static List<String> createJarWithRenamedClassesFor(Path pth, List<Class<?>> includedClasses) {
        return createJarWithRenamedClassesFor(pth, includedClasses, List.of());
    }
}
