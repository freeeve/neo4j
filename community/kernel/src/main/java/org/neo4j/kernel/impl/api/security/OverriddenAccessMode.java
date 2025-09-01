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
 * Access mode that overrides the original access mode with the overriding mode. Allows exactly what the overriding
 * mode allows, while retaining the meta data of the original mode only.
 */
public class OverriddenAccessMode extends WrappedAccessMode {
    public OverriddenAccessMode(AccessMode original, StaticAccessMode overriding) {
        super(original, overriding);
    }

    @Override
    public boolean allowsWrites() {
        return wrapping.allowsWrites();
    }

    @Override
    public PermissionState allowsTokenCreates(PrivilegeAction action) {
        return wrapping.allowsTokenCreates(action);
    }

    @Override
    public boolean allowsSchemaWrites() {
        return wrapping.allowsSchemaWrites();
    }

    @Override
    public PermissionState allowsSchemaWrites(PrivilegeAction action) {
        return wrapping.allowsSchemaWrites(action);
    }

    @Override
    public boolean allowsShowIndex() {
        return wrapping.allowsShowIndex();
    }

    @Override
    public boolean allowsShowConstraint() {
        return wrapping.allowsShowConstraint();
    }

    @Override
    public boolean allowsTraverseAllLabels() {
        return wrapping.allowsTraverseAllLabels();
    }

    @Override
    public boolean allowsTraverseAllNodesWithLabel(int label) {
        return wrapping.allowsTraverseAllNodesWithLabel(label);
    }

    @Override
    public boolean disallowsTraverseLabel(int label) {
        return wrapping.disallowsTraverseLabel(label);
    }

    @Override
    public boolean allowsTraverseNode(int... labels) {
        return wrapping.allowsTraverseNode(labels);
    }

    @Override
    public boolean hasApplicableTraverseNodeAllowPropertyRules(int label) {
        return wrapping.hasApplicableTraverseNodeAllowPropertyRules(label);
    }

    @Override
    public boolean allowsTraverseNode(LabelsSupplier labels, SelectedPropertiesProvider selectedPropertiesProvider) {
        return wrapping.allowsTraverseNode(labels, selectedPropertiesProvider);
    }

    @Override
    public boolean allowsTraverseAllRelTypes() {
        return wrapping.allowsTraverseAllRelTypes();
    }

    @Override
    public boolean allowsTraverseRelType(int relType) {
        return wrapping.allowsTraverseRelType(relType);
    }

    @Override
    public boolean allowsTraverseAllRelsWithType(int relType) {
        return wrapping.allowsTraverseAllRelsWithType(relType);
    }

    @Override
    public boolean disallowsTraverseRelType(int relType) {
        return wrapping.disallowsTraverseRelType(relType);
    }

    @Override
    public boolean hasApplicableTraverseRelAllowPropertyRules(int type) {
        return wrapping.hasApplicableTraverseRelAllowPropertyRules(type);
    }

    @Override
    public boolean allowsTraverseRelationship(int type, SelectedPropertiesProvider propertyProviderSupplier) {
        return wrapping.allowsTraverseRelationship(type, propertyProviderSupplier);
    }

    @Override
    public boolean allowsReadNodeProperties(
            LabelsSupplier labels, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider) {
        return wrapping.allowsReadNodeProperties(labels, propertyKeys, propertyProvider);
    }

    @Override
    public boolean allowsTraverseAndReadAllMatchingNodeProperties(int[] labels, int[] propertyKeys) {
        return wrapping.allowsTraverseAndReadAllMatchingNodeProperties(labels, propertyKeys);
    }

    @Override
    public boolean allowsTraverseAndReadAllMatchingRelProperties(int[] relTypes, int[] propertyKeys) {
        return wrapping.allowsTraverseAndReadAllMatchingRelProperties(relTypes, propertyKeys);
    }

    @Override
    public boolean allowsReadRelProperties(
            RelTypeSupplier relType, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider) {
        return wrapping.allowsReadRelProperties(relType, propertyKeys, propertyProvider);
    }

    @Override
    public IntPredicate allowedToReadNodeProperties(
            LabelsSupplier labels, Supplier<SelectedPropertiesProvider> propertyProvider, PropertySelection selection) {
        return wrapping.allowedToReadNodeProperties(labels, propertyProvider, selection);
    }

    @Override
    public IntPredicate allowedToReadRelationshipProperties(
            RelTypeSupplier relType,
            Supplier<SelectedPropertiesProvider> propertyProvider,
            PropertySelection selection) {
        return wrapping.allowedToReadRelationshipProperties(relType, propertyProvider, selection);
    }

    @Override
    public boolean allowsSeePropertyKeyToken(int propertyKey) {
        return wrapping.allowsSeePropertyKeyToken(propertyKey);
    }

    @Override
    public PermissionState allowsShowSetting(String setting) {
        return wrapping.allowsShowSetting(setting);
    }

    @Override
    public boolean allowsSetLabel(int labelId) {
        return wrapping.allowsSetLabel(labelId);
    }

    @Override
    public boolean allowsRemoveLabel(int labelId) {
        return wrapping.allowsRemoveLabel(labelId);
    }

    @Override
    public boolean allowsCreateNode(int[] labelIds) {
        return wrapping.allowsCreateNode(labelIds);
    }

    @Override
    public boolean allowsDeleteNode(Supplier<TokenSet> labelSupplier) {
        return wrapping.allowsDeleteNode(labelSupplier);
    }

    @Override
    public boolean allowsCreateRelationship(int relType) {
        return wrapping.allowsCreateRelationship(relType);
    }

    @Override
    public boolean allowsDeleteRelationship(int relType) {
        return wrapping.allowsDeleteRelationship(relType);
    }

    @Override
    public boolean allowsSetProperty(LabelsSupplier labels, int propertyKey) {
        return wrapping.allowsSetProperty(labels, propertyKey);
    }

    @Override
    public boolean allowsSetProperty(RelTypeSupplier relType, int propertyKey) {
        return wrapping.allowsSetProperty(relType, propertyKey);
    }

    @Override
    public PermissionState allowsLoadAllData() {
        return wrapping.allowsLoadAllData();
    }

    @Override
    public PermissionState allowsLoadUri(URI url, InetAddress inetAddress) {
        return wrapping.allowsLoadUri(url, inetAddress);
    }

    @Override
    public String name() {
        return MessageUtil.overriddenMode(original.name(), wrapping.name());
    }

    public String authTokenWrappingName() {
        return wrapping.name();
    }
}
