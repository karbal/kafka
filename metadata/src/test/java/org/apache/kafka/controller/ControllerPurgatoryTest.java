/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.controller;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ControllerPurgatoryTest {
    @Rule
    final public Timeout globalTimeout = Timeout.seconds(40);

    static class SampleDeferredEvent implements DeferredEvent {
        private final CompletableFuture<Void> future = new CompletableFuture<>();

        @Override
        public void complete(Exception exception) {
            if (exception != null) {
                future.completeExceptionally(exception);
            } else {
                future.complete(null);
            }
        }

        CompletableFuture<Void> future() {
            return future;
        }
    }

    @Test
    public void testCompleteEvents() {
        ControllerPurgatory purgatory = new ControllerPurgatory();
        SampleDeferredEvent event1 = new SampleDeferredEvent();
        SampleDeferredEvent event2 = new SampleDeferredEvent();
        SampleDeferredEvent event3 = new SampleDeferredEvent();
        purgatory.add(1, event1);
        purgatory.add(1, event2);
        purgatory.add(3, event3);
        purgatory.completeUpTo(2);
        assertTrue(event1.future.isDone());
        assertTrue(event2.future.isDone());
        assertFalse(event3.future.isDone());
        purgatory.completeUpTo(4);
        assertTrue(event3.future.isDone());
    }

    @Test
    public void testFailOnIncorrectOrdering() {
        ControllerPurgatory purgatory = new ControllerPurgatory();
        SampleDeferredEvent event1 = new SampleDeferredEvent();
        SampleDeferredEvent event2 = new SampleDeferredEvent();
        purgatory.add(2, event1);
        assertThrows(RuntimeException.class, () -> purgatory.add(1, event2));
    }

    @Test
    public void testFailEvents() {
        ControllerPurgatory purgatory = new ControllerPurgatory();
        SampleDeferredEvent event1 = new SampleDeferredEvent();
        SampleDeferredEvent event2 = new SampleDeferredEvent();
        SampleDeferredEvent event3 = new SampleDeferredEvent();
        purgatory.add(1, event1);
        purgatory.add(3, event2);
        purgatory.add(3, event3);
        purgatory.completeUpTo(2);
        assertTrue(event1.future.isDone());
        assertFalse(event2.future.isDone());
        assertFalse(event3.future.isDone());
        purgatory.failAll(new RuntimeException("failed"));
        assertTrue(event2.future.isDone());
        assertTrue(event3.future.isDone());
        assertEquals(RuntimeException.class, assertThrows(ExecutionException.class,
            () -> event2.future.get()).getCause().getClass());
        assertEquals(RuntimeException.class, assertThrows(ExecutionException.class,
            () -> event3.future.get()).getCause().getClass());
    }
}
