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
package org.neo4j.tooling.procedure.extension;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.create;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CompilationExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS = create(CompilationExtension.class);

    private static final JavaFileObject DUMMY =
            com.google.testing.compile.JavaFileObjects.forSourceLines("Dummy", "final class Dummy {}");

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        CompilerState state = new CompilerState();
        context.getStore(NS).put("state", state);
        state.start();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        CompilerState state = context.getStore(NS).remove("state", CompilerState.class);
        if (state != null) {
            state.finish();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
        Class<?> type = pc.getParameter().getType();
        return type == Elements.class || type == Types.class;
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
        CompilerState state = ec.getStore(NS).get("state", CompilerState.class);

        if (state == null || state.envRef.get() == null) {
            throw new ParameterResolutionException("Compiler not initialized");
        }

        ProcessingEnvironment env = state.envRef.get();

        if (pc.getParameter().getType() == Elements.class) {
            return env.getElementUtils();
        } else if (pc.getParameter().getType() == Types.class) {
            return env.getTypeUtils();
        }

        throw new ParameterResolutionException("Unsupported parameter");
    }

    static class CompilerState {
        final AtomicReference<ProcessingEnvironment> envRef = new AtomicReference<>();
        final CountDownLatch initialized = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);

        Thread thread;
        volatile Throwable error;

        void start() throws InterruptedException {
            thread = new Thread(
                    () -> {
                        try {
                            com.google.testing.compile.Compiler.javac()
                                    .withProcessors(new ProcessorImpl())
                                    .compile(DUMMY);
                        } catch (Throwable t) {
                            error = t;
                            initialized.countDown(); // ensure test thread unblocks on failure
                        }
                    },
                    "minimal-compiler-thread");

            thread.setDaemon(true);
            thread.start();

            initialized.await();

            if (error != null) {
                throw new RuntimeException("Compilation failed during initialization", error);
            }
        }

        void finish() throws InterruptedException {
            done.countDown();

            if (thread != null) {
                thread.join();
            }

            if (error != null) {
                throw new RuntimeException("Compilation failed during execution", error);
            }
        }

        class ProcessorImpl extends AbstractProcessor {

            @Override
            public synchronized void init(ProcessingEnvironment env) {
                super.init(env);
                envRef.set(env);
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (roundEnv.processingOver()) {
                    initialized.countDown(); // signal after all rounds complete — javac state is fully ready
                    try {
                        done.await(); // keep compiler alive during test
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        envRef.set(null); // cleanup
                    }
                }
                return false;
            }

            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return singleton("*");
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.latest();
            }
        }
    }
}
