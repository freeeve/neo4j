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
package org.neo4j.kernel.impl.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.AbstractNeo4jTestCase;

class TestProperties extends AbstractNeo4jTestCase {
    private static final int VALUE_RANGE_SPLIT = 20;

    @Test
    void addAndRemovePropertiesWithinOneTransaction() {
        String node = createNode();

        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.setProperty("name", "oscar");
            txNode.setProperty("favourite_numbers", new Long[] {1L, 2L, 3L});
            txNode.setProperty("favourite_colors", new String[] {"blue", "red"});
            txNode.removeProperty("favourite_colors");
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            assertNotNull(transaction.getNodeByElementId(node).getProperty("favourite_numbers", null));
        }
    }

    @Test
    void addAndRemovePropertiesWithinOneTransaction2() {
        String node = createNode();

        try (Transaction transaction = getGraphDb().beginTx()) {
            transaction.getNodeByElementId(node).setProperty("foo", "bar");
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.setProperty("foo2", "bar");
            txNode.removeProperty("foo");
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            assertThrows(
                    NotFoundException.class,
                    () -> transaction.getNodeByElementId(node).getProperty("foo"));
        }
    }

    @Test
    void removeAndAddSameProperty() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            transaction.getNodeByElementId(node).setProperty("foo", "bar");
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.removeProperty("foo");
            txNode.setProperty("foo", "bar");
            transaction.commit();
        }
        try (Transaction transaction = getGraphDb().beginTx()) {
            assertEquals("bar", transaction.getNodeByElementId(node).getProperty("foo"));
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.setProperty("foo", "bar");
            txNode.removeProperty("foo");
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            assertNull(transaction.getNodeByElementId(node).getProperty("foo", null));
        }
    }

    @Test
    void removeSomeAndSetSome() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            transaction.getNodeByElementId(node).setProperty("remove me", "trash");
            transaction.commit();
        }
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.removeProperty("remove me");
            txNode.setProperty("foo", "bar");
            txNode.setProperty("baz", 17);
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            assertEquals("bar", txNode.getProperty("foo"));
            assertEquals(17, txNode.getProperty("baz"));
            assertNull(txNode.getProperty("remove me", null));
            transaction.commit();
        }
    }

    @Test
    void removeOneOfThree() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.setProperty("1", 1);
            txNode.setProperty("2", 2);
            txNode.setProperty("3", 3);
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            transaction.getNodeByElementId(node).removeProperty("2");
            transaction.commit();
        }
        try (Transaction transaction = getGraphDb().beginTx()) {
            assertNull(transaction.getNodeByElementId(node).getProperty("2", null));
            transaction.commit();
        }
    }

    @Test
    void testLongPropertyValues() {
        String n = createNode();
        setPropertyAndAssertIt(n, -134217728L);
        setPropertyAndAssertIt(n, -134217729L);
    }

    @Test
    void testIntPropertyValues() {
        String n = createNode();
        setPropertyAndAssertIt(n, -134217728);
        setPropertyAndAssertIt(n, -134217729);
    }

    @Test
    void booleanRange() {
        String node = createNode();
        setPropertyAndAssertIt(node, false);
        setPropertyAndAssertIt(node, true);
    }

    @Test
    void byteRange() {
        String node = createNode();
        byte stride = Byte.MAX_VALUE / VALUE_RANGE_SPLIT;
        for (byte i = Byte.MIN_VALUE; i < Byte.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            i = i > 0 && Byte.MAX_VALUE - i < stride ? Byte.MAX_VALUE : (byte) (i + stride);
        }
    }

    @Test
    void charRange() {
        String node = createNode();
        char stride = Character.MAX_VALUE / VALUE_RANGE_SPLIT;
        for (char i = Character.MIN_VALUE; i < Character.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            i = i > 0 && Character.MAX_VALUE - i < stride ? Character.MAX_VALUE : (char) (i + stride);
        }
    }

    @Test
    void shortRange() {
        String node = createNode();
        short stride = Short.MAX_VALUE / VALUE_RANGE_SPLIT;
        for (short i = Short.MIN_VALUE; i < Short.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            i = i > 0 && Short.MAX_VALUE - i < stride ? Short.MAX_VALUE : (short) (i + stride);
        }
    }

    @Test
    void intRange() {
        String node = createNode();
        int stride = Integer.MAX_VALUE / VALUE_RANGE_SPLIT;
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            i = i > 0 && Integer.MAX_VALUE - i < stride ? Integer.MAX_VALUE : i + stride;
        }
    }

    @Test
    void longRange() {
        String node = createNode();
        long stride = Long.MAX_VALUE / VALUE_RANGE_SPLIT;
        for (long i = Long.MIN_VALUE; i < Long.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            i = i > 0 && Long.MAX_VALUE - i < stride ? Long.MAX_VALUE : i + stride;
        }
    }

    @Test
    void floatRange() {
        String node = createNode();
        float stride = 16f;
        for (float i = Float.MIN_VALUE; i < Float.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            setPropertyAndAssertIt(node, -i);
            i *= stride;
        }
    }

    @Test
    void doubleRange() {
        String node = createNode();
        double stride = 4194304d; // 2^23
        for (double i = Double.MIN_VALUE; i < Double.MAX_VALUE; ) {
            setPropertyAndAssertIt(node, i);
            setPropertyAndAssertIt(node, -i);
            i *= stride;
        }
    }

    private void setPropertyAndAssertIt(String nodeId, Object value) {
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(nodeId);
            txNode.setProperty("key", value);
            assertEquals(value, txNode.getProperty("key"));
            transaction.commit();
        }
    }

    @Test
    void loadManyProperties() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            for (int i = 0; i < 200; i++) {
                txNode.setProperty("property " + i, "value");
            }
            transaction.commit();
        }
        try (Transaction transaction = getGraphDb().beginTx()) {
            assertEquals("value", transaction.getNodeByElementId(node).getProperty("property 0"));
            transaction.commit();
        }
    }

    @Test
    void name() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.setProperty("name", "yo");
            txNode.getProperty("name");
            transaction.commit();
        }

        try (Transaction tx = getGraphDb().beginTx()) {
            tx.getNodeByElementId(node).getProperty("name");
            tx.commit();
        }
    }
}
