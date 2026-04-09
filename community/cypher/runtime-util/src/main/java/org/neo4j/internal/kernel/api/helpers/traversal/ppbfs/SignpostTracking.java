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
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs;

import java.util.BitSet;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks;
import org.neo4j.memory.MemoryTracker;

public interface SignpostTracking {
    boolean isProtectedFromPruning(SignpostStack stack);

    boolean canAbandonTraceBranch(SignpostStack stack);

    void onPushed(TwoWaySignpost signpost, SignpostStack stack);

    void onPopped(TwoWaySignpost signpost, SignpostStack stack);

    boolean validate(SignpostStack stack);

    void clear();

    static SignpostTracking trailMode(MemoryTracker memoryTracker, PPBFSHooks hooks) {
        return new TrailModeSignPostTracking(memoryTracker, hooks);
    }

    static SignpostTracking walkMode() {
        return NO_TRACKING;
    }

    SignpostTracking NO_TRACKING = new SignpostTracking() {
        @Override
        public boolean isProtectedFromPruning(SignpostStack stack) {
            return false;
        }

        @Override
        public boolean canAbandonTraceBranch(SignpostStack stack) {
            return false;
        }

        @Override
        public void onPushed(TwoWaySignpost signpost, SignpostStack stack) {
            // do nothing
        }

        @Override
        public boolean validate(SignpostStack stack) {
            return true;
        }

        @Override
        public void onPopped(TwoWaySignpost signpost, SignpostStack stack) {
            // do nothing
        }

        @Override
        public void clear() {
            // do nothing
        }
    };

    final class TrailModeSignPostTracking implements SignpostTracking {
        private final HeapTrackingLongObjectHashMap<BitSet> relationshipPresenceAtDepth;
        private final BitSet protectFromPruning;
        private final PPBFSHooks hooks;

        TrailModeSignPostTracking(MemoryTracker memoryTracker, PPBFSHooks hooks) {
            this.relationshipPresenceAtDepth = HeapTrackingLongObjectHashMap.createLongObjectHashMap(memoryTracker);
            this.protectFromPruning = new BitSet();
            this.hooks = hooks;
        }

        @Override
        public boolean isProtectedFromPruning(SignpostStack stack) {
            return protectFromPruning.get(stack.size());
        }

        /** this function allows us to abandon a trace branch early. if we have detected a duplicate relationship then
         * the set of paths we're currently tracing are all invalid and so we should be able to abort tracing them, except
         * tracing also performs verification/validation.
         *
         * if the current node is validated then further tracing has no benefit, so we can pop back to the previous
         * node.
         * */
        @Override
        public boolean canAbandonTraceBranch(SignpostStack stack) {
            int dup = distanceToDuplicate(stack.headSignpost());

            if (dup == 0) {
                return false;
            }

            int sourceLength = stack.lengthFromSource();
            for (int i = 0; i <= dup; i++) {
                var candidate = stack.signpost(stack.size() - 1 - i);

                if (!candidate.prevNode.validatedAtLength(sourceLength)) {
                    return false;
                }

                sourceLength += candidate.dataGraphLength();
            }

            this.protectFromPruning.set(stack.size() - 1 - dup, stack.size() - 1, true);
            return true;
        }

        @Override
        public void onPushed(TwoWaySignpost signpost, SignpostStack stack) {
            int size = stack.size();
            this.protectFromPruning.set(size - 1, false);
            if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                var depths = this.relationshipPresenceAtDepth.get(rel.relId);
                if (depths == null) {
                    depths = new BitSet();
                    this.relationshipPresenceAtDepth.put(rel.relId, depths);
                }
                depths.set(size - 1);
            }
        }

        @Override
        public boolean validate(SignpostStack stack) {
            int sourceLength = 0;
            for (int i = stack.size() - 1; i >= 0; i--) {
                TwoWaySignpost signpost = stack.signpost(i);
                sourceLength += signpost.dataGraphLength();
                if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                    var bitset = relationshipPresenceAtDepth.get(rel.relId);
                    assert bitset.get(i);
                    if (bitset.length() > i + 1) {
                        hooks.invalidTrail(stack);
                        return false;
                    }
                }

                if (!signpost.isValidatedAtLength(sourceLength)) {
                    signpost.validate(sourceLength);
                    if (!signpost.forwardNode.validatedAtLength(sourceLength)) {
                        signpost.forwardNode.setValidatedAtLength(sourceLength, stack.dgLength() - sourceLength);
                    }
                }
            }
            return true;
        }

        @Override
        public void onPopped(TwoWaySignpost signpost, SignpostStack stack) {
            if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                var depths = relationshipPresenceAtDepth.get(rel.relId);
                depths.clear(stack.size());
                if (depths.isEmpty()) {
                    relationshipPresenceAtDepth.remove(rel.relId);
                }
            }
        }

        private int distanceToDuplicate(TwoWaySignpost signpost) {
            if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                var stack = relationshipPresenceAtDepth.get(rel.relId);
                if (stack == null) {
                    return 0;
                }
                int last = stack.length();
                if (last == 0) {
                    return 0;
                }

                int next = stack.previousSetBit(last - 2);
                if (next == -1) {
                    return 0;
                }
                return last - 1 - next;
            }
            return 0;
        }

        @Override
        public void clear() {
            relationshipPresenceAtDepth.clear();
        }
    }
}
