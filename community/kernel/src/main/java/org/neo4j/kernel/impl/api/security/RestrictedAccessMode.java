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
package org.neo4j.kernel.impl.api.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.neo4j.internal.kernel.api.LabelsSupplier;
import org.neo4j.internal.kernel.api.RelTypeSupplier;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.internal.kernel.api.security.PermissionState;
import org.neo4j.internal.kernel.api.security.PrivilegeAction;
import org.neo4j.internal.kernel.api.security.SelectedPropertiesProvider;
import org.neo4j.internal.kernel.api.security.StaticAccessMode;
import org.neo4j.messages.MessageUtil;
import org.neo4j.storageengine.api.PropertySelection;

/**
 * Access mode that restricts the original access mode with the restricting mode. Allows things that both the
 * original and the restricting mode allows, while retaining the meta data of the original mode only.
 */
public class RestrictedAccessMode extends WrappedAccessMode {
    public RestrictedAccessMode(AccessMode original, StaticAccessMode restricting) {
        super(original, restricting);
    }

    @Override
    public boolean allowsWrites() {
        return original.allowsWrites() && wrapping.allowsWrites();
    }

    @Override
    public PermissionState allowsTokenCreates(PrivilegeAction action) {
        return original.allowsTokenCreates(action).restrict(wrapping.allowsTokenCreates(action));
    }

    @Override
    public boolean allowsSchemaWrites() {
        return original.allowsSchemaWrites() && wrapping.allowsSchemaWrites();
    }

    @Override
    public PermissionState allowsSchemaWrites(PrivilegeAction action) {
        return original.allowsSchemaWrites(action).restrict(wrapping.allowsSchemaWrites(action));
    }

    @Override
    public boolean allowsShowIndex() {
        return original.allowsShowIndex() && wrapping.allowsShowIndex();
    }

    @Override
    public boolean allowsShowConstraint() {
        return original.allowsShowConstraint() && wrapping.allowsShowConstraint();
    }

    @Override
    public boolean allowsTraverseAllLabels() {
        return original.allowsTraverseAllLabels() && wrapping.allowsTraverseAllLabels();
    }

    @Override
    public boolean allowsTraverseAllNodesWithLabel(int label) {
        return original.allowsTraverseAllNodesWithLabel(label) && wrapping.allowsTraverseAllNodesWithLabel(label);
    }

    @Override
    public boolean disallowsTraverseLabel(int label) {
        return original.disallowsTraverseLabel(label) || wrapping.disallowsTraverseLabel(label);
    }

    @Override
    public boolean hasNoTraverseNodePrivilege() {
        return original.hasNoTraverseNodePrivilege() && wrapping.hasNoTraverseNodePrivilege();
    }

    @Override
    public boolean allowsTraverseNode(int... labels) {
        return original.allowsTraverseNode(labels) && wrapping.allowsTraverseNode(labels);
    }

    @Override
    public boolean hasApplicableTraverseNodeAllowPropertyRules(int label) {
        return original.hasApplicableTraverseNodeAllowPropertyRules(label)
                || wrapping.hasApplicableTraverseNodeAllowPropertyRules(label);
    }

    @Override
    public boolean allowsTraverseNode(LabelsSupplier labels, SelectedPropertiesProvider selectedPropertiesProvider) {
        return original.allowsTraverseNode(labels, selectedPropertiesProvider)
                && wrapping.allowsTraverseNode(labels, selectedPropertiesProvider);
    }

    @Override
    public boolean allowsTraverseAllRelTypes() {
        return original.allowsTraverseAllRelTypes() && wrapping.allowsTraverseAllRelTypes();
    }

    @Override
    public boolean allowsTraverseRelType(int relType) {
        return original.allowsTraverseRelType(relType) && wrapping.allowsTraverseRelType(relType);
    }

    @Override
    public boolean allowsTraverseAllRelsWithType(int relType) {
        return original.allowsTraverseAllRelsWithType(relType) && wrapping.allowsTraverseAllRelsWithType(relType);
    }

    @Override
    public boolean disallowsTraverseRelType(int relType) {
        return original.disallowsTraverseRelType(relType) && wrapping.disallowsTraverseRelType(relType);
    }

    @Override
    public boolean hasApplicableTraverseRelAllowPropertyRules(int type) {
        return original.hasApplicableTraverseRelAllowPropertyRules(type)
                || wrapping.hasApplicableTraverseRelAllowPropertyRules(type);
    }

    @Override
    public boolean allowsTraverseRelationship(int type, SelectedPropertiesProvider propertyProviderSupplier) {
        return original.allowsTraverseRelationship(type, propertyProviderSupplier)
                && wrapping.allowsTraverseRelationship(type, propertyProviderSupplier);
    }

    @Override
    public boolean allowsReadNodeProperties(
            LabelsSupplier labels, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider) {
        return original.allowsReadNodeProperties(labels, propertyKeys, propertyProvider)
                && wrapping.allowsReadNodeProperties(labels, propertyKeys, propertyProvider);
    }

    @Override
    public IntPredicate allowedToReadNodeProperties(
            LabelsSupplier labels, Supplier<SelectedPropertiesProvider> propertyProvider, PropertySelection selection) {
        return original.allowedToReadNodeProperties(labels, propertyProvider, selection)
                .and(wrapping.allowedToReadNodeProperties(labels, propertyProvider, selection));
    }

    @Override
    public IntPredicate allowedToReadRelationshipProperties(
            RelTypeSupplier relType,
            Supplier<SelectedPropertiesProvider> propertyProvider,
            PropertySelection selection) {
        return original.allowedToReadRelationshipProperties(relType, propertyProvider, selection)
                .and(wrapping.allowedToReadRelationshipProperties(relType, propertyProvider, selection));
    }

    @Override
    public boolean allowsTraverseAndReadAllMatchingNodeProperties(int[] labels, int[] propertyKeys) {
        return original.allowsTraverseAndReadAllMatchingNodeProperties(labels, propertyKeys)
                && wrapping.allowsTraverseAndReadAllMatchingNodeProperties(labels, propertyKeys);
    }

    @Override
    public boolean allowsTraverseAndReadAllMatchingRelProperties(int[] relTypes, int[] propertyKeys) {
        return original.allowsTraverseAndReadAllMatchingRelProperties(relTypes, propertyKeys)
                && wrapping.allowsTraverseAndReadAllMatchingRelProperties(relTypes, propertyKeys);
    }

    @Override
    public boolean allowsReadRelProperties(
            RelTypeSupplier relType, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider) {
        return original.allowsReadRelProperties(relType, propertyKeys, propertyProvider)
                && wrapping.allowsReadRelProperties(relType, propertyKeys, propertyProvider);
    }

    @Override
    public boolean allowsSeePropertyKeyToken(int propertyKey) {
        return original.allowsSeePropertyKeyToken(propertyKey) && wrapping.allowsSeePropertyKeyToken(propertyKey);
    }

    @Override
    public PermissionState allowsShowSetting(String setting) {
        return original.allowsShowSetting(setting).restrict(wrapping.allowsShowSetting(setting));
    }

    @Override
    public boolean allowsSetLabel(int labelId) {
        return original.allowsSetLabel(labelId) && wrapping.allowsSetLabel(labelId);
    }

    @Override
    public boolean allowsRemoveLabel(int labelId) {
        return original.allowsRemoveLabel(labelId) && wrapping.allowsRemoveLabel(labelId);
    }

    @Override
    public boolean allowsCreateNode(int[] labelIds) {
        return original.allowsCreateNode(labelIds) && wrapping.allowsCreateNode(labelIds);
    }

    @Override
    public boolean allowsDeleteNode(Supplier<TokenSet> labelSupplier) {
        return original.allowsDeleteNode(labelSupplier) && wrapping.allowsDeleteNode(labelSupplier);
    }

    @Override
    public boolean allowsCreateRelationship(int relType) {
        return original.allowsCreateRelationship(relType) && wrapping.allowsCreateRelationship(relType);
    }

    @Override
    public boolean allowsDeleteRelationship(int relType) {
        return original.allowsDeleteRelationship(relType) && wrapping.allowsDeleteRelationship(relType);
    }

    @Override
    public boolean allowsSetProperty(LabelsSupplier labels, int propertyKey) {
        return original.allowsSetProperty(labels, propertyKey) && wrapping.allowsSetProperty(labels, propertyKey);
    }

    @Override
    public boolean allowsSetProperty(RelTypeSupplier relType, int propertyKey) {
        return original.allowsSetProperty(relType, propertyKey) && wrapping.allowsSetProperty(relType, propertyKey);
    }

    @Override
    public PermissionState allowsLoadAllData() {
        return original.allowsLoadAllData().restrict(wrapping.allowsLoadAllData());
    }

    @Override
    public PermissionState allowsLoadUri(URI url, InetAddress inetAddress) {
        return original.allowsLoadUri(url, inetAddress).restrict(wrapping.allowsLoadUri(url, inetAddress));
    }

    @Override
    public String name() {
        return MessageUtil.restrictedMode(original.name(), wrapping.name());
    }

    private static PropertySelection combinePropertySelections(
            PropertySelection originalSelection, PropertySelection wrappingSelection) {
        MutableIntSet union = IntSets.mutable.empty();
        for (int i = 0; i < originalSelection.numberOfKeys(); i++) {
            union.add(originalSelection.key(i));
        }

        for (int i = 0; i < wrappingSelection.numberOfKeys(); i++) {
            union.add(wrappingSelection.key(i));
        }

        return PropertySelection.selection(union.toArray());
    }
}
