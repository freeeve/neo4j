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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThreadSanitizerTest {

    AtomicInteger exceptionCounter = new AtomicInteger();

    @BeforeEach
    void setup() {
        exceptionCounter = new AtomicInteger();
    }

    @Test
    void shouldSanitizeMethodFromProxiedInterface() {
        CountDownLatch latch = new CountDownLatch(2);
        DummyTypeA dummy = new DummyImpl(latch);
        DummyTypeA proxiedDummy =
                ThreadSanitizer.sanitize(dummy, DummyTypeA.class, e -> exceptionCounter.incrementAndGet());
        Runnable doThing = () -> {
            try {
                proxiedDummy.typeADoThing();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        assertProxyIsSanitized(doThing);
    }

    @Test
    void shouldSanitizeMultipleMethodsFromProxiedInterface() {
        CountDownLatch latch = new CountDownLatch(2);
        DummyTypeA dummy = new DummyImpl(latch);
        DummyTypeA proxiedDummy =
                ThreadSanitizer.sanitize(dummy, DummyTypeA.class, e -> exceptionCounter.incrementAndGet());
        Runnable doThing = () -> {
            try {
                proxiedDummy.doThing();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        Runnable doThingTypeA = () -> {
            try {
                proxiedDummy.typeADoThing();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        assertProxyIsSanitized(doThing, doThingTypeA);
    }

    @Test
    void shouldSanitizeMethodFromParentOfProxiedInterface() {
        CountDownLatch latch = new CountDownLatch(2);
        DummyTypeA dummy = new DummyImpl(latch);
        DummyTypeA proxiedDummy =
                ThreadSanitizer.sanitize(dummy, DummyTypeA.class, e -> exceptionCounter.incrementAndGet());
        Runnable doThing = () -> {
            try {
                proxiedDummy.doThing();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        assertProxyIsSanitized(doThing);
    }

    @Test
    void shouldNotComplainAboutSequentialAccessRegardlessOfThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        DummyTypeA dummy = new DummyImpl(latch);
        DummyTypeA proxiedDummy =
                ThreadSanitizer.sanitize(dummy, DummyTypeA.class, e -> exceptionCounter.incrementAndGet());
        Runnable doThing = () -> {
            proxiedDummy.typeADoThingWithoutWaiting();
            latch.countDown();
        };

        Thread t = new Thread(doThing);
        t.start();
        latch.await();

        // Sequential access from another thread does not throw
        doThing.run();
        assertThat(exceptionCounter.get()).isEqualTo(0);

        // Sequential access from same thread does not throw
        doThing.run();
        assertThat(exceptionCounter.get()).isEqualTo(0);
    }

    @Test
    void shouldNotComplainAboutInternalRecursion() throws InterruptedException {
        // GIVEN
        DummyTypeB dummy = new DummyImpl(new CountDownLatch(0));
        DummyTypeB proxiedDummy =
                ThreadSanitizer.sanitize(dummy, DummyTypeB.class, e -> exceptionCounter.incrementAndGet());

        // WHEN
        proxiedDummy.recurse();

        // THEN - No exceptions, as we only enter the proxy from outside once
        assertThat(exceptionCounter.get()).isEqualTo(0);
    }

    @Test
    void doesComplainAboutExternalRecursion() {
        // GIVEN
        DummyTypeB dummy = new DummyImpl(new CountDownLatch(0));
        DummyTypeB proxiedDummy =
                ThreadSanitizer.sanitize(dummy, DummyTypeB.class, e -> exceptionCounter.incrementAndGet());

        // WHEN
        proxiedDummy.recurse(proxiedDummy::recurse);

        // THEN - We get exceptions because the proxy is re-entered (even though it's the same thread doing so).
        assertThat(exceptionCounter.get()).isEqualTo(2);
    }

    void assertProxyIsSanitized(Runnable proxyRunnable) {
        // WHEN - Current thread and a new thread are both executing in doThing simultaneously
        Thread t = new Thread(proxyRunnable);
        t.start();
        proxyRunnable.run();

        // THEN - Exception handler has seen two exceptions, one for each of the two threads concurrently in doThing.
        assertThat(exceptionCounter.get()).isEqualTo(2);
    }

    void assertProxyIsSanitized(Runnable proxyRunnable1, Runnable proxyRunnable2) {
        // WHEN - Current thread and a new thread are both executing in two different methods simultaneously
        Thread t = new Thread(proxyRunnable1);
        t.start();
        proxyRunnable2.run();

        // THEN - Exception handler has seen two exceptions, one for each of the two threads concurrently in doThing.
        assertThat(exceptionCounter.get()).isEqualTo(2);
    }

    public interface DummyRoot {
        void doThing() throws InterruptedException;
    }

    public interface DummyTypeA extends DummyRoot {
        void typeADoThing() throws InterruptedException;

        void typeADoThingWithoutWaiting();
    }

    public interface DummyTypeB extends DummyRoot {
        void recurse();

        void recurse(Runnable runnable);
    }

    public static class DummyImpl implements DummyTypeA, DummyTypeB {

        final CountDownLatch latch;

        int recurseCount = 5;

        public DummyImpl(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void doThing() throws InterruptedException {
            latch.countDown();
            latch.await();
        }

        @Override
        public void typeADoThing() throws InterruptedException {
            latch.countDown();
            latch.await();
        }

        @Override
        public void typeADoThingWithoutWaiting() {}

        @Override
        public void recurse() {
            if (recurseCount-- > 0) {
                recurse();
            }
        }

        @Override
        public void recurse(Runnable runnable) {
            if (recurseCount-- > 0) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
