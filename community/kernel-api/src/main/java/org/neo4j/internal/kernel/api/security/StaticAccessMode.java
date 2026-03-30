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
package org.neo4j.internal.kernel.api.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import org.neo4j.internal.kernel.api.LabelsSupplier;
import org.neo4j.internal.kernel.api.RelTypeSupplier;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.storageengine.api.PropertySelection;

public enum StaticAccessMode implements AccessMode {
    /**
     * No reading or writing allowed.
     */
    ACCESS(false, false, false, false, false),
    /**
     * No reading or writing allowed because of expired credentials.
     */
    CREDENTIALS_EXPIRED(false, false, false, false, false),

    /**
     * Allows reading data and schema, but not writing.
     */
    READ(true, false, false, false, false),
    /**
     * Allows writing data
     */
    WRITE_ONLY(false, true, false, false, false),
    /**
     * Allows reading and writing data, but not schema.
     */
    WRITE(true, true, false, false, false),
    /**
     * Allows reading and writing data and creating new tokens, but not schema.
     */
    TOKEN_WRITE(true, true, true, false, false),
    /**
     * Allows reading and writing data and creating new tokens and changing schema.
     */
    SCHEMA(true, true, true, true, false),
    /**
     * Allows all operations.
     */
    FULL(true, true, true, true, true);

    private final boolean read;
    private final boolean write;
    private final boolean token;
    private final boolean schema;
    private final boolean procedureBoost;

    StaticAccessMode(boolean read, boolean write, boolean token, boolean schema, boolean procedureBoost) {
        this.read = read;
        this.write = write;
        this.token = token;
        this.schema = schema;
        this.procedureBoost = procedureBoost;
    }

    @Override
    public boolean allowsWrites() {
        return write;
    }

    @Override
    public PermissionState allowsTokenCreates(PrivilegeAction action) {
        return PermissionState.fromAllowList(token);
    }

    @Override
    public boolean allowsSchemaWrites() {
        return schema;
    }

    @Override
    public PermissionState allowsSchemaWrites(PrivilegeAction action) {
        return PermissionState.fromAllowList(schema);
    }

    @Override
    public boolean allowsShowIndex() {
        return schema;
    }

    @Override
    public boolean allowsShowConstraint() {
        return schema;
    }

    @Override
    public boolean allowsTraverseAllLabels() {
        return read;
    }

    @Override
    public boolean allowsTraverseAllNodesWithLabel(int label) {
        return read;
    }

    @Override
    public boolean disallowsTraverseLabel(int label) {
        return false;
    }

    @Override
    public boolean hasNoTraverseNodePrivilege() {
        return !read;
    }

    @Override
    public boolean allowsTraverseNode(int... labels) {
        return read;
    }

    @Override
    public boolean hasApplicableTraverseNodeAllowPropertyRules(int label) {
        return read;
    }

    @Override
    public boolean allowsTraverseNode(LabelsSupplier labels, SelectedPropertiesProvider selectedPropertiesProvider) {
        return read;
    }

    @Override
    public boolean allowsTraverseAllRelTypes() {
        return read;
    }

    @Override
    public boolean allowsTraverseRelType(int relType) {
        return read;
    }

    @Override
    public boolean allowsTraverseAllRelsWithType(int relType) {
        return read;
    }

    @Override
    public boolean disallowsTraverseRelType(int relType) {
        return false;
    }

    @Override
    public boolean hasApplicableTraverseRelAllowPropertyRules(int type) {
        return read;
    }

    @Override
    public boolean allowsTraverseRelationship(int type, SelectedPropertiesProvider propertyProviderSupplier) {
        return read;
    }

    @Override
    public boolean allowsReadNodeProperties(
            LabelsSupplier labels, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider) {
        return read;
    }

    @Override
    public IntPredicate allowedToReadNodeProperties(
            LabelsSupplier labels, Supplier<SelectedPropertiesProvider> propertyProvider, PropertySelection selection) {
        return key -> read;
    }

    @Override
    public boolean allowsTraverseAndReadAllMatchingNodeProperties(int[] labels, int[] propertyKeys) {
        return read;
    }

    @Override
    public boolean allowsTraverseAndReadAllMatchingRelProperties(int[] relTypes, int[] propertyKeys) {
        return read;
    }

    @Override
    public boolean allowsReadRelProperties(
            RelTypeSupplier relType, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider) {
        return read;
    }

    @Override
    public IntPredicate allowedToReadRelationshipProperties(
            RelTypeSupplier relType,
            Supplier<SelectedPropertiesProvider> propertyProvider,
            PropertySelection selection) {
        return key -> read;
    }

    @Override
    public boolean allowsSeePropertyKeyToken(int propertyKey) {
        return read;
    }

    @Override
    public PermissionState allowsExecuteProcedure(int procedureId) {
        return PermissionState.EXPLICIT_GRANT;
    }

    @Override
    public PermissionState allowExecuteAdminProcedures() {
        return PermissionState.EXPLICIT_GRANT;
    }

    @Override
    public PermissionState shouldBoostProcedure(int procedureId) {
        return PermissionState.fromAllowList(procedureBoost);
    }

    @Override
    public PermissionState allowsExecuteFunction(int id) {
        return PermissionState.EXPLICIT_GRANT;
    }

    @Override
    public PermissionState shouldBoostFunction(int id) {
        return PermissionState.fromAllowList(procedureBoost);
    }

    @Override
    public PermissionState allowsExecuteAggregatingFunction(int id) {
        return PermissionState.EXPLICIT_GRANT;
    }

    @Override
    public PermissionState shouldBoostAggregatingFunction(int id) {
        return PermissionState.fromAllowList(procedureBoost);
    }

    @Override
    public PermissionState allowsShowSetting(String setting) {
        return PermissionState.EXPLICIT_GRANT;
    }

    @Override
    public boolean allowsSetLabel(int labelId) {
        return write;
    }

    @Override
    public boolean allowsRemoveLabel(int labelId) {
        return write;
    }

    @Override
    public boolean allowsCreateNode(int[] labelIds) {
        return write;
    }

    @Override
    public boolean allowsDeleteNode(Supplier<TokenSet> labelSupplier) {
        return write;
    }

    @Override
    public boolean allowsCreateRelationship(int relType) {
        return write;
    }

    @Override
    public boolean allowsDeleteRelationship(int relType) {
        return write;
    }

    @Override
    public boolean allowsSetProperty(LabelsSupplier labels, int propertyKey) {
        return write;
    }

    @Override
    public boolean allowsSetProperty(RelTypeSupplier relType, int propertyKey) {
        return write;
    }

    @Override
    public PermissionState allowsLoadAllData() {
        return PermissionState.fromAllowList(read);
    }

    @Override
    public PermissionState allowsLoadUri(URI uri, InetAddress inetAddress) {
        return PermissionState.fromAllowList(read);
    }
}
