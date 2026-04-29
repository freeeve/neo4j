/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.util.buildtools;

import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Post-processes Scala-generated {@code .class} files after compilation so mixed Scala 2.13 / Scala 3 / TeaVM
 * classpaths can link, without defining duplicate Scala overloads that become ambiguous when {@link
 * org.neo4j.cypher.internal.util.Rewriter Rewriter} extends {@code Function1}.
 *
 * <ul>
 *   <li><b>{@code Rewriter$}</b> — two JVM {@code lift} overloads:
 *       {@code (Lscala/PartialFunction;)Lscala/Function1;} (historical / TeaVM) and
 *       {@code (Lscala/PartialFunction;)LRewriter;} (Scala 3 expected type {@code Rewriter})</li>
 *   <li><b>{@code Rewritable$RewritableAny$}</b> — {@code endoRewrite$extension(Object, Rewriter)} delegating to
 *       {@code endoRewrite$extension(Object, Function1)} for the same reason</li>
 * </ul>
 */
public final class RewriterLiftJvmBridgeInstaller {

    private static final String REWRITER = "org/neo4j/cypher/internal/util/Rewriter";
    private static final String REWRITER_DOLLAR = "org/neo4j/cypher/internal/util/Rewriter$";
    private static final String LIFT_FUNCTION1_DESC = "(Lscala/PartialFunction;)Lscala/Function1;";
    private static final String LIFT_REWRITER_DESC = "(Lscala/PartialFunction;)L" + REWRITER + ";";

    private static final String RW_ANY_DOLLAR = "org/neo4j/cypher/internal/util/Rewritable$RewritableAny$";
    private static final String ENDO_REWRITE_FUNCTION1_DESC = "(Ljava/lang/Object;Lscala/Function1;)Ljava/lang/Object;";
    private static final String ENDO_REWRITE_REWRITER_DESC = "(Ljava/lang/Object;L" + REWRITER + ";)Ljava/lang/Object;";

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : "target/classes");
        patchRewriterLift(root);
        patchRewritableEndoRewrite(root);
    }

    private static void patchRewriterLift(Path root) throws Exception {
        Path classFile = root.resolve(REWRITER_DOLLAR + ".class");
        if (!Files.isRegularFile(classFile)) {
            throw new IllegalStateException("Missing class file: " + classFile.toAbsolutePath());
        }
        byte[] original = Files.readAllBytes(classFile);
        byte[] patched = addRewriterLiftBridgeIfMissing(original);
        if (patched != null) {
            Files.write(classFile, patched);
        }
    }

    private static void patchRewritableEndoRewrite(Path root) throws Exception {
        Path classFile = root.resolve(RW_ANY_DOLLAR + ".class");
        if (!Files.isRegularFile(classFile)) {
            throw new IllegalStateException("Missing class file: " + classFile.toAbsolutePath());
        }
        byte[] original = Files.readAllBytes(classFile);
        byte[] patched = addEndoRewriteRewriterBridgeIfMissing(original);
        if (patched != null) {
            Files.write(classFile, patched);
        }
    }

    private static byte[] addRewriterLiftBridgeIfMissing(byte[] original) {
        ClassReader cr = new ClassReader(original);
        LiftBridgePresenceChecker checker = new LiftBridgePresenceChecker();
        cr.accept(checker, ClassReader.SKIP_CODE);
        if (checker.hasRewriterReturnLift) {
            return null;
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new LiftBridgeAddingVisitor(cw), ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static byte[] addEndoRewriteRewriterBridgeIfMissing(byte[] original) {
        ClassReader cr = new ClassReader(original);
        EndoBridgePresenceChecker checker = new EndoBridgePresenceChecker();
        cr.accept(checker, ClassReader.SKIP_CODE);
        if (checker.hasRewriterArgEndoRewrite) {
            return null;
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new EndoBridgeAddingVisitor(cw), ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static final class LiftBridgePresenceChecker extends ClassVisitor {

        boolean hasRewriterReturnLift;

        LiftBridgePresenceChecker() {
            super(Opcodes.ASM9, null);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            if ("lift".equals(name) && LIFT_REWRITER_DESC.equals(descriptor)) {
                hasRewriterReturnLift = true;
            }
            return null;
        }
    }

    private static final class LiftBridgeAddingVisitor extends ClassVisitor {

        LiftBridgeAddingVisitor(ClassWriter cw) {
            super(Opcodes.ASM9, cw);
        }

        @Override
        public void visitEnd() {
            MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                    "lift",
                    LIFT_REWRITER_DESC,
                    null,
                    null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, REWRITER_DOLLAR, "lift", LIFT_FUNCTION1_DESC, false);
            mv.visitTypeInsn(Opcodes.CHECKCAST, REWRITER);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            super.visitEnd();
        }
    }

    private static final class EndoBridgePresenceChecker extends ClassVisitor {

        boolean hasRewriterArgEndoRewrite;

        EndoBridgePresenceChecker() {
            super(Opcodes.ASM9, null);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            if ("endoRewrite$extension".equals(name) && ENDO_REWRITE_REWRITER_DESC.equals(descriptor)) {
                hasRewriterArgEndoRewrite = true;
            }
            return null;
        }
    }

    private static final class EndoBridgeAddingVisitor extends ClassVisitor {

        EndoBridgeAddingVisitor(ClassWriter cw) {
            super(Opcodes.ASM9, cw);
        }

        @Override
        public void visitEnd() {
            MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                    "endoRewrite$extension",
                    ENDO_REWRITE_REWRITER_DESC,
                    null,
                    null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL, RW_ANY_DOLLAR, "endoRewrite$extension", ENDO_REWRITE_FUNCTION1_DESC, false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            super.visitEnd();
        }
    }

    private RewriterLiftJvmBridgeInstaller() {}
}
