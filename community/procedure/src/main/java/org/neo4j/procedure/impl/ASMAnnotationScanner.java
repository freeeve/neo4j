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
package org.neo4j.procedure.impl;

import java.io.IOException;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ASMAnnotationScanner {
    private final Set<String> scannedForDescriptors;

    private static class AnnotationFoundException extends RuntimeException {
        AnnotationFoundException() {
            super("User plugin method found", null, false, false);
        }
    }

    ASMAnnotationScanner(Set<Class<?>> annotationsToScanFor) {
        scannedForDescriptors =
                annotationsToScanFor.stream().map(Class::descriptorString).collect(Collectors.toSet());
    }

    private static class ScannerClassVisitor extends ClassVisitor {
        private final Set<String> scannedForDescriptors;

        ScannerClassVisitor(Set<String> scannedForDescriptors) {
            super(Opcodes.ASM9);
            this.scannedForDescriptors = scannedForDescriptors;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            return new ScannerMethodVisitor(scannedForDescriptors);
        }
    }

    private static class ScannerMethodVisitor extends MethodVisitor {
        private final Set<String> scannedForDescriptors;

        ScannerMethodVisitor(Set<String> scannedForDescriptors) {
            super(Opcodes.ASM9);
            this.scannedForDescriptors = scannedForDescriptors;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (scannedForDescriptors.contains(descriptor)) {
                // Immediately stop further ClassReader visiting and signal we found a desired annotation
                throw new AnnotationFoundException();
            }
            // no need to scan for details of annotation
            return null;
        }
    }

    private boolean processClass(byte[] bytes) {
        var reader = new ClassReader(bytes);
        var classVisitor = new ScannerClassVisitor(scannedForDescriptors);
        try {
            // Choose options to reduce parsing of details we don't need
            reader.accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
        } catch (AnnotationFoundException ex) {
            return true;
        }
        return false;
    }

    public boolean checkIfClassContainsAnnotation(JarFile jar, JarEntry entry) {
        if (!entry.getName().endsWith(".class")) {
            return false;
        }
        try (var stream = jar.getInputStream(entry)) {
            var bytes = stream.readAllBytes();
            return processClass(bytes);
        } catch (IOException ex) {
            return false;
        }
    }
}
