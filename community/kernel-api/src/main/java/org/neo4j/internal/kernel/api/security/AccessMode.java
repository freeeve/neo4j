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
import java.util.Collections;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import org.neo4j.internal.kernel.api.LabelsSupplier;
import org.neo4j.internal.kernel.api.RelTypeSupplier;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.storageengine.api.PropertySelection;

/**
 * Controls the capabilities of a KernelTransaction.
 */
public interface AccessMode {

    /**
     * Check whether the executing user has full write access.
     *
     * @return true if the the user has full write access
     */
    boolean allowsWrites();

    /*
     * Check whether the executing user is allowed to create new tokens in the token store of the provided type.
     *
     * @param action the type of token to check. PrivilegeAction.CREATE_LABEL, PrivilegeAction.CREATE_PROPERTYKEY
     * and PrivilegeAction.CREATE_RELTYPE are valid here.
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsTokenCreates(PrivilegeAction action);

    /**
     * Check whether the executing user has any schema write access (INDEX or CONSTRAINTS)
     * @return true if the executing user has any schema write access
     */
    boolean allowsSchemaWrites();

    /*
     * Check whether the executing user has permission to execute the specified schema write action
     *
     * @param action the schema write action to check.
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsSchemaWrites(PrivilegeAction action);

    /**
     * Check whether the executing user has permission to call SHOW INDEX
     *
     * @return true if the executing user has permission
     */
    boolean allowsShowIndex();

    /**
     * Check whether the executing user has permission to call SHOW CONSTRAINTS
     *
     * @return true if the executing user has permission
     */
    boolean allowsShowConstraint();

    /**
     * true if all nodes can be traversed
     */
    boolean allowsTraverseAllLabels();

    /**
     * true if all nodes with this label always can be traversed
     */
    boolean allowsTraverseAllNodesWithLabel(int label);

    /**
     * true if this label is deny-listed for traversal
     */
    boolean disallowsTraverseLabel(int label);

    /**
     * true if there are no read privileges granted
     */
    boolean hasNoTraverseNodePrivilege();

    /**
     * true if a particular node with exactly these labels can be traversed.
     *
     * @param labels the labels on the node to be checked. If labels only contains {@link org.neo4j.token.api.TokenConstants#ANY_LABEL} it will work
     *               the same as {@link #allowsTraverseAllLabels}
     */
    boolean allowsTraverseNode(int... labels);

    /**
     * checks whether there is potential for nodes with this label to be traversed subject of property-based
     * GRANTS evaluating to true and not being precluded by label-based DENYs.
     * @param label - the label to check permissions for
     * @return true when nodes with this label could be traversable due to property-based GRANTS
     */
    boolean hasApplicableTraverseNodeAllowPropertyRules(int label);

    /**
     * Checks whether traversal of the node is allowed based on its labels and properties.
     * Checks labels-based traverse rules and the property based traverse rules.
     * Uses the {@code propertyProviderFacroty} to get the node property values and the {@code labels} to get the relevant property rules,
     * and then evaluates the property rules to determine whether the node can be traversed. Also checks label-based traverse rules.
     *
     * @param labels                       labels of the node. Used to determine which property rules need to be checked.
     * @param selectedPropertiesProvider provider of the scrutinee node's properties
     * @return {@code true} if traversal of this node is allowed
     */
    boolean allowsTraverseNode(LabelsSupplier labels, SelectedPropertiesProvider selectedPropertiesProvider);

    /**
     * true if all relationships can be traversed
     */
    boolean allowsTraverseAllRelTypes();

    /**
     * true if the relType can be traversed.
     *
     * @param relType the relationship type to check access for. If relType is {@link org.neo4j.token.api.TokenConstants#ANY_RELATIONSHIP_TYPE} it will work
     *                the same as {@link #allowsTraverseAllRelTypes}
     */
    boolean allowsTraverseRelType(int relType);

    /**
     * true if *all* relationships with the relType can be traversed.
     * this includes check for {@link #allowsTraverseAllLabels()} as it pre-requesite to traverse relationship
     *
     * @param relType the relationship type to check access for
     */
    boolean allowsTraverseAllRelsWithType(int relType);

    /**
     * true if the relType is deny-listed for traversal.
     *
     * @param relType the relationship type to check access for.
     */
    boolean disallowsTraverseRelType(int relType);

    /**
     * checks whether there is potential for relationships with this type to be traversed subject of property-based
     * GRANTS evaluating to true and not being precluded by type-based DENYs.
     * @param type - the type to check permissions for
     * @return true when relationships with this type could be traversable due to property-based GRANTS
     */
    boolean hasApplicableTraverseRelAllowPropertyRules(int type);

    /**
     * Checks whether traversal of the relationship is allowed based on its type and properties.
     * Checks type-based traverse rules and the property based traverse rules.
     * Uses the {@code propertyProviderFacroty} to get the relationship property values and the {@code type} to get the relevant property rules,
     * and then evaluates the property rules to determine whether the relationship can be traversed. Also checks type-based traverse rules.
     *
     * @param type                       the type of the relationship. Used to determine which property rules need to be checked.
     * @param selectedPropertiesProvider provider of the scrutinee relationship's properties
     * @return {@code true} if traversal of this relationship is allowed
     */
    boolean allowsTraverseRelationship(int type, SelectedPropertiesProvider selectedPropertiesProvider);

    /**
     * determines whether the authenticated principal is allowed to read the specified {@code propertyKeys} according
     * to the property-based RBAC read rules AND the label-based RBAC rules.
     * Optimised for a multi-property reads.
     * @param labels the labels of the node in question. Used to determine which RBAC rules are applicable.
     * @param propertyKeys the properties which the principal is requesting to read
     * @param propertyProvider the provider of the node's property values. Used as operands for the property rules.
     * @return {@code true} if the principal is allowed to read ALL of the requested {@code propertyKeys}
     */
    boolean allowsReadNodeProperties(
            LabelsSupplier labels, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider);

    /**
     * Returns predicate that determines whether the authenticated principal is allowed to read the specified {@code propertyKey}
     * according to the property-based RBAC read rules AND the label-based RBAC rules.
     * @param labels the labels of the node in question. Used to determine which RBAC rules are applicable.
     * @param propertyProvider the provider of the node's property values. Used as operands for the property rules.
     * @param selection property selection requested to read.
     * @return {@code IntPredicate} which when applied to {@code propertyKey} answers {@code true} if the principal is allowed to read that {@code propertyKey}
     */
    IntPredicate allowedToReadNodeProperties(
            LabelsSupplier labels, Supplier<SelectedPropertiesProvider> propertyProvider, PropertySelection selection);

    /**
     * Check that the user is allowed to access all nodes and properties described by given labels and properties.
     * Positive result means specific checks for individual entities can be ommitted, a.k.a. security shortcut.
     *
     * @param labels the labels of the nodes in question
     * @param propertyKeys the properties which the principal is requesting to read
     * @return {@code true} if there is no restictions affecting described set of entities
     */
    boolean allowsTraverseAndReadAllMatchingNodeProperties(int[] labels, int[] propertyKeys);

    /**
     * Check that the user is allowed to access all relationships and properties described by given relationship types and properties.
     * Positive result means specific checks for individual entities can be ommitted, a.k.a. security shortcut.
     *
     * @param relTypes the types of the relationships in question
     * @param propertyKeys the properties which the principal is requesting to read
     * @return {@code true} if there is no restictions affecting described set of entities
     */
    boolean allowsTraverseAndReadAllMatchingRelProperties(int[] relTypes, int[] propertyKeys);

    /**
     * determines whether the authenticated principal is allowed to read the specified {@code propertyKeys} according
     * to the property-based RBAC read rules AND the type-based RBAC rules.
     * Optimised for a multi-property reads.
     * @param relType the type of the relationship in question. Used to determine which RBAC rules are applicable.
     * @param propertyKeys the properties which the principal is requesting to read
     * @param propertyProvider the provider of the relationship's property values. Used as operands for the property rules.
     * @return {@code true} if the principal is allowed to read ALL of the requested {@code propertyKeys}
     */
    boolean allowsReadRelProperties(
            RelTypeSupplier relType, int[] propertyKeys, Supplier<SelectedPropertiesProvider> propertyProvider);

    /**
     * Returns predicate that determines whether the authenticated principal is allowed to read the specified {@code propertyKey}
     * according to the property-based RBAC read rules AND the label-based RBAC rules.
     * @param relType the type of the relationship in question. Used to determine which RBAC rules are applicable.
     * @param propertyProvider the provider of the relationship's property values. Used as operands for the property rules.
     * @param selection property selection requested to read.
     * @return {@code IntPredicate} which when applied to {@code propertyKey} answers {@code true} if the principal is allowed to read that {@code propertyKey}
     */
    IntPredicate allowedToReadRelationshipProperties(
            RelTypeSupplier relType,
            Supplier<SelectedPropertiesProvider> propertyProvider,
            PropertySelection selection);

    boolean allowsSeePropertyKeyToken(int propertyKey);

    /**
     * Check if execution of a procedure is allowed
     *
     * @param procedureId id of the procedure
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsExecuteProcedure(int procedureId);

    /**
     * Check if the 'execute admin procedures' privilege is granted.
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowExecuteAdminProcedures();

    /**
     * Check if execution of a procedure should be done with boosted privileges.
     * <p>
     * <strong>Note: this does not check if execution is allowed</strong>
     *
     * @param procedureId id of the procedure
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState shouldBoostProcedure(int procedureId);

    /**
     * Check if execution of a user defined function is allowed
     *
     * @param id id of the function
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsExecuteFunction(int id);

    /**
     * Check if execution of a user defined function should be done with boosted privileges.
     * <p>
     * <strong>Note: this does not check if execution is allowed</strong>
     *
     * @param id id of the function
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState shouldBoostFunction(int id);

    /**
     * Check if execution of a aggregating user defined function is allowed
     *
     * @param id id of the function
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsExecuteAggregatingFunction(int id);

    /**
     * Check if execution of a aggregating user defined function should be done with boosted privileges.
     * <p>
     * <strong>Note: this does not check if execution is allowed</strong>
     *
     * @param id id of the function
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState shouldBoostAggregatingFunction(int id);

    /**
     * Check if a given setting is available to the executing user
     *
     * @param setting name of the setting
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsShowSetting(String setting);

    /**
     * Check if the executing user is allowed to set the label with the supplied label id.
     *
     * @param labelId the id of the label to check.
     * @return true if the executing user is allowed to set that label
     */
    boolean allowsSetLabel(int labelId);

    /**
     * Check if the executing user is allowed to remove the label with the supplied label id.
     *
     * @param labelId the id of the label to check.
     * @return true if the executing user is allowed to remove that label
     */
    boolean allowsRemoveLabel(int labelId);

    /**
     * Check if the executing user is allowed to create a node with the supplied label id(s).
     *
     * @param labelIds the ids of the labels to check.
     * @return true if the executing user is allowed to create this node.
     */
    boolean allowsCreateNode(int[] labelIds);

    /**
     * Check if the executing user is allowed to delete a node with the supplied label id(s).
     *
     * @param labelSupplier a function supplying the label id(s) to check.
     * @return true if the executing user is allowed to delete this node
     */
    boolean allowsDeleteNode(Supplier<TokenSet> labelSupplier);

    /**
     * Check if the executing user is allowed to create a relationship with the supplied relationship type.
     *
     * @param relType the id of the relationship type to check.
     * @return true if the executing user is allowed to create this relationship
     */
    boolean allowsCreateRelationship(int relType);

    /**
     * Check if the executing user is allowed to delete a relationship with the supplied relationship type.
     *
     * @param relType the id of the relationship type to check.
     * @return true if the executing user is allowed to delete this relationship
     */
    boolean allowsDeleteRelationship(int relType);

    /**
     * Check if the executing user is allowed to set a property on a node.
     *
     * @param labels the set of labels on the node
     * @param propertyKey the id of the property key the user wishes to set
     * @return true if the executing user is allowed to set this property
     */
    boolean allowsSetProperty(LabelsSupplier labels, int propertyKey);

    /**
     * Check if the executing user is allowed to set a property on a relationship.
     *
     * @param relType the relationship type of the relationship
     * @param propertyKey the id of the property key the user wishes to set
     * @return true if the executing user is allowed to set this property
     */
    boolean allowsSetProperty(RelTypeSupplier relType, int propertyKey);

    /**
     * Check if the executing user has permission to use LOAD CSV from any location
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsLoadAllData();

    /**
     * Check if the executing user has permission to use LOAD CSV from the specified location
     *
     * @return PermissionState.EXPLICIT_GRANT if the user has been granted permission with a GRANT rule,
     * PermissionState.EXPLICIT_DENY if the user is denied due to a DENY rule and PermissionState.NOT_GRANTED
     * if no relevent privileges have been found.
     */
    PermissionState allowsLoadUri(URI url, InetAddress inetAddress);

    String name();

    /**
     * Return the set of role names used to populate this AccessMode
     *
     * @return a set of role names
     */
    default Set<String> roles() {
        return Collections.emptySet();
    }

    default boolean isOverridden() {
        return false;
    }

    /**
     * Return true if this AccessMode contains a full set of privileges and is thus cacheable.
     * @return
     */
    default boolean isCacheable() {
        return false;
    }
}
