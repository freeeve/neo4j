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
package org.neo4j.storageengine.api;

import static org.neo4j.token.api.TokenConstants.NO_TOKEN;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.neo4j.collection.PrimitiveArrays;

/**
 * Specifies criteria for which properties to select.
 */
public abstract sealed class PropertySelection {
    public static final int UNKNOWN_NUMBER_OF_KEYS = -1;

    protected final boolean fallbackKeysOnly;
    protected final IntPredicate valueSelection;

    protected PropertySelection(boolean keysOnly, IntPredicate valueSelection) {
        this.fallbackKeysOnly = keysOnly;
        this.valueSelection = valueSelection;
    }

    /**
     * @return {@code true} if this selection limits which keys will be selected, otherwise {@code false} if all will be selected.
     */
    public abstract boolean isLimited();

    /**
     * @return the number of keys in this selection. Selections that are not discrete returns {@code -1}.
     */
    public abstract int numberOfKeys();

    public boolean isEmpty() {
        return numberOfKeys() == 0;
    }

    /**
     * @param index the selection index. A selection can have multiple keys.
     * @return the key for the given selection index.
     */
    public abstract int key(int index);

    /**
     * @param key the key to tests whether it fits the criteria of this selection.
     * @return {@code true} if the given {@code key} is part of this selection, otherwise {@code false}.
     */
    public abstract boolean test(int key);

    /**
     * @param key the key ID to check whether its value should be read and included in the selection.
     * @return whether to include the value for the given key in this selection.
     */
    public boolean includeValue(int key) {
        return valueSelection == null ? !fallbackKeysOnly : valueSelection.test(key);
    }

    /**
     * @return lowest key in this selection.
     */
    public abstract int lowestKey();

    /**
     * @return highest key in this selection.
     */
    public abstract int highestKey();

    /**
     * @return a {@link PropertySelection} instance that excludes keys from the existing set of keys.
     */
    public abstract PropertySelection excluding(int... keysToExclude);

    @Override
    public String toString() {
        return String.format("Property%sSelection", fallbackKeysOnly ? "Key" : "");
    }

    /**
     * Creates a {@link PropertySelection} with its single criterion based on the given {@code key}.
     *
     * @param key a single key that should be selected.
     * @return a {@link PropertySelection} instance with the given {@code key} as its criterion.
     */
    public static PropertySelection selection(int key) {
        return SingleKey.singleKey(false, key);
    }

    /**
     * Creates a {@link PropertySelection} with its criteria based on the given {@code keys}.
     * Will include values for all the given keys.
     *
     * @param keys one or more keys that should be part of the created selection.
     * @return a {@link PropertySelection} instance with the given {@code keys} as its criteria.
     */
    public static PropertySelection selection(int... keys) {
        return multiSelection(false, null, keys);
    }

    /**
     * Creates a {@link PropertySelection} with its criteria based on the given {@code keys}.
     *
     * @param valueSelection which values for the given keys to include.
     * If {@code null} then all values for the selected keys will be included.
     * @param keys one or more keys that should be part of the created selection.
     * @return a {@link PropertySelection} instance with the given {@code keys} as its criteria.
     */
    public static PropertySelection selection(IntPredicate valueSelection, int... keys) {
        return multiSelection(false, valueSelection, keys);
    }

    /**
     * Creates a {@link PropertySelection} with its criteria based on the given {@code keys}.
     * This selection will hint that only the keys are interesting, not the values.
     *
     * @param keys one or more keys that should be part of the created selection.
     * @return a {@link PropertySelection} instance with the given {@code keys} as its criteria.
     */
    public static PropertySelection onlyKeysSelection(int... keys) {
        return multiSelection(true, null, keys);
    }

    private static PropertySelection multiSelection(boolean fallbackKeysOnly, IntPredicate valueSelection, int[] keys) {
        if (keys == null) {
            if (valueSelection == null) {
                return fallbackKeysOnly ? ALL_PROPERTY_KEYS : ALL_PROPERTIES;
            }
            return allProperties(fallbackKeysOnly, valueSelection);
        }
        if (keys.length == 0) {
            return NO_PROPERTIES;
        }
        if (keys.length == 1) {
            int key = keys[0];
            if (valueSelection == null) {
                return key == NO_TOKEN ? NO_PROPERTIES : SingleKey.singleKey(fallbackKeysOnly, key);
            } else {
                return SingleKey.singleKey(!valueSelection.test(key), key);
            }
        }
        return new MultipleKeys(fallbackKeysOnly, valueSelection, keys);
    }

    private static final class SingleKey extends PropertySelection {
        private static final int LOW_ID_THRESHOLD = 128;
        private static final PropertySelection[] SINGLE_LOW_ID_SELECTIONS = new PropertySelection[LOW_ID_THRESHOLD];
        private static final PropertySelection[] SINGLE_LOW_ID_KEY_SELECTIONS = new PropertySelection[LOW_ID_THRESHOLD];

        static {
            for (int key = 0; key < SINGLE_LOW_ID_SELECTIONS.length; key++) {
                SINGLE_LOW_ID_SELECTIONS[key] = new PropertySelection.SingleKey(false, key);
                SINGLE_LOW_ID_KEY_SELECTIONS[key] = new PropertySelection.SingleKey(true, key);
            }
        }

        private static PropertySelection singleKey(boolean fallbackKeysOnly, int key) {
            if (key < LOW_ID_THRESHOLD && key >= 0) {
                return fallbackKeysOnly ? SINGLE_LOW_ID_KEY_SELECTIONS[key] : SINGLE_LOW_ID_SELECTIONS[key];
            }
            return new SingleKey(fallbackKeysOnly, key);
        }

        private final int key;

        private SingleKey(boolean keysOnly, int key) {
            super(keysOnly, null);
            this.key = key;
        }

        @Override
        public boolean isLimited() {
            return true;
        }

        @Override
        public int numberOfKeys() {
            return 1;
        }

        @Override
        public int key(int index) {
            assert index == 0;
            return key;
        }

        @Override
        public boolean test(int key) {
            return this.key == key;
        }

        @Override
        public int lowestKey() {
            return key;
        }

        @Override
        public int highestKey() {
            return key;
        }

        @Override
        public PropertySelection excluding(int... keysToExclude) {
            for (int k : keysToExclude) {
                if (k == key) {
                    return NO_PROPERTIES;
                }
            }
            return this;
        }

        @Override
        public String toString() {
            return super.toString() + "[" + key + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SingleKey singleKey)) return false;
            return key == singleKey.key;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(key);
        }
    }

    private static final class MultipleKeys extends PropertySelection {
        private final int[] keys;

        private MultipleKeys(boolean fallbackKeysOnly, IntPredicate valueSelection, int[] keys) {
            super(fallbackKeysOnly, valueSelection);
            this.keys = cloneAndCleanUp(keys);
        }

        @Override
        public boolean isLimited() {
            return true;
        }

        @Override
        public int numberOfKeys() {
            return keys.length;
        }

        @Override
        public int key(int index) {
            assert index >= 0 && index < keys.length;
            return keys[index];
        }

        @Override
        public boolean test(int key) {
            for (int k : keys) {
                if (k == key) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int lowestKey() {
            return keys[0];
        }

        @Override
        public int highestKey() {
            return keys[keys.length - 1];
        }

        @Override
        public PropertySelection excluding(int... keysToExclude) {
            if (keysToExclude.length == 0) {
                return this;
            }
            int[] cleanedUpKeysToExclude = cloneAndCleanUp(keysToExclude);
            if (cleanedUpKeysToExclude.length == 0) {
                return this;
            }

            int[] result = PrimitiveArrays.subtract(keys, cleanedUpKeysToExclude);
            if (result.length == keys.length) {
                return this;
            }
            return PropertySelection.multiSelection(fallbackKeysOnly, valueSelection, result);
        }

        @Override
        public String toString() {
            return super.toString() + "[" + Arrays.toString(keys) + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MultipleKeys that)) return false;
            return Arrays.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(keys);
        }
    }

    private static final class AllExcept extends PropertySelection {
        private final int[] excluded;

        /**
         * @param excluded must have gone through {@link #cloneAndCleanUp(int[])}.
         */
        AllExcept(boolean fallbackKeysOnly, IntPredicate valueSelection, int... excluded) {
            super(fallbackKeysOnly, valueSelection);
            this.excluded = excluded;
        }

        @Override
        public boolean isLimited() {
            return true;
        }

        @Override
        public int numberOfKeys() {
            return UNKNOWN_NUMBER_OF_KEYS;
        }

        @Override
        public int key(int index) {
            throw new IllegalStateException("This selection has no discrete number of keys");
        }

        @Override
        public boolean test(int key) {
            for (int k : excluded) {
                if (k == key) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int lowestKey() {
            return 0;
        }

        @Override
        public int highestKey() {
            return Integer.MAX_VALUE;
        }

        @Override
        public PropertySelection excluding(int... keysToExclude) {
            if (keysToExclude.length == 0) {
                return this;
            }
            int[] cleanedUpKeysToExclude = cloneAndCleanUp(keysToExclude);
            if (cleanedUpKeysToExclude.length == 0) {
                return this;
            }

            int[] result = PrimitiveArrays.union(excluded, cleanedUpKeysToExclude);
            if (result.length == excluded.length) {
                return this;
            }
            // No need to call cloneAndCleanUp again, since the union logic already took care of sorting and uniqueness
            return new AllExcept(fallbackKeysOnly, valueSelection, result);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AllExcept allExcept)) return false;
            return Arrays.equals(excluded, allExcept.excluded);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(excluded);
        }
    }

    private static final class NoPropertiesSelection extends PropertySelection {

        private NoPropertiesSelection() {
            super(true, null);
        }

        @Override
        public boolean isLimited() {
            return true;
        }

        @Override
        public int numberOfKeys() {
            return 0;
        }

        @Override
        public int key(int index) {
            throw new IllegalStateException("This selection has no keys");
        }

        @Override
        public boolean test(int key) {
            return false;
        }

        @Override
        public int lowestKey() {
            return -1;
        }

        @Override
        public int highestKey() {
            return -1;
        }

        @Override
        public PropertySelection excluding(int... keysToExclude) {
            return this;
        }
    }

    private static final class AllPropertiesSelection extends PropertySelection {
        private AllPropertiesSelection(boolean keysOnly, IntPredicate valueSelection) {
            super(keysOnly, valueSelection);
        }

        @Override
        public boolean isLimited() {
            return false;
        }

        @Override
        public int numberOfKeys() {
            return UNKNOWN_NUMBER_OF_KEYS;
        }

        @Override
        public int key(int index) {
            return NO_TOKEN;
        }

        @Override
        public boolean test(int key) {
            return true;
        }

        @Override
        public int lowestKey() {
            return 0;
        }

        @Override
        public int highestKey() {
            return Integer.MAX_VALUE;
        }

        @Override
        public PropertySelection excluding(int... keysToExclude) {
            if (keysToExclude.length == 0) {
                return this;
            }
            int[] cleanedUpKeysToExclude = cloneAndCleanUp(keysToExclude);
            if (cleanedUpKeysToExclude.length == 0) {
                return this;
            }
            return new AllExcept(fallbackKeysOnly, valueSelection, cleanedUpKeysToExclude);
        }

        @Override
        public String toString() {
            return super.toString() + "[*]";
        }
    }

    public static final PropertySelection ALL_PROPERTIES = allProperties(false, null);
    public static final PropertySelection ALL_PROPERTY_KEYS = allProperties(true, null);
    public static final PropertySelection NO_PROPERTIES = new NoPropertiesSelection();

    private static PropertySelection allProperties(boolean keysOnly, IntPredicate valueSelection) {
        return new AllPropertiesSelection(keysOnly, valueSelection);
    }

    /**
     * Clones the {@code suppliedKeys} for safety, since it's coming from "user" call site.
     * The cloned keys are sorted and also any -1 values (likely coming from name->id lookup
     * returning {@link org.neo4j.token.api.TokenConstants#NO_TOKEN}) are removed.
     *
     * @param suppliedKeys must be a non-empty array
     */
    private static int[] cloneAndCleanUp(int[] suppliedKeys) {
        var keys = suppliedKeys.clone();
        Arrays.sort(keys);
        if (keys[0] == NO_TOKEN) {
            int start = 1;
            while (start < keys.length && keys[start] == NO_TOKEN) {
                start++;
            }
            keys = Arrays.copyOfRange(keys, start, keys.length);
        }
        return keys;
    }
}
