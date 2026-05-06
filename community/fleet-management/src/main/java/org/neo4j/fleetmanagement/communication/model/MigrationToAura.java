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
package org.neo4j.fleetmanagement.communication.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

@JsonClassDescription("Details of a migration to Aura.")
public class MigrationToAura {
    @JsonProperty("migration_id")
    @JsonPropertyDescription("Unique identifier for the migration")
    public String migrationId;

    @JsonProperty("status")
    @JsonPropertyDescription("Current status of the migration")
    public String status;

    @JsonProperty("source_database_name")
    @JsonPropertyDescription("Name of the source database to be migrated")
    public String sourceDatabaseName;

    @JsonProperty("target_aura_instance_id")
    @JsonPropertyDescription("ID of the target Aura instance")
    public String targetAuraInstanceId;

    @JsonProperty("target_database_name")
    @JsonPropertyDescription("Name of the target database in Aura")
    public String targetDatabaseName;

    @JsonProperty("created_at")
    @JsonPropertyDescription("Timestamp when the migration was created")
    public Date createdAt;

    @JsonProperty("instance_creation_started_at")
    @JsonPropertyDescription("Timestamp when the target Aura instance creation started")
    public Date instanceCreationStartedAt;

    @JsonProperty("instance_creation_completed_at")
    @JsonPropertyDescription("Timestamp when the target Aura instance creation completed")
    public Date instanceCreationCompletedAt;

    @JsonProperty("instance_creation_error_message")
    @JsonPropertyDescription("Error message from instance creation, if any")
    public String instanceCreationErrorMessage;

    @JsonProperty("dump_started_at")
    @JsonPropertyDescription("Timestamp when dump creation started")
    public Date dumpStartedAt;

    @JsonProperty("dump_completed_at")
    @JsonPropertyDescription("Timestamp when dump creation completed")
    public Date dumpCompletedAt;

    @JsonProperty("dump_error_message")
    @JsonPropertyDescription("Error message from dump creation, if any")
    public String dumpErrorMessage;

    @JsonProperty("upload_started_at")
    @JsonPropertyDescription("Timestamp when upload started")
    public Date uploadStartedAt;

    @JsonProperty("upload_completed_at")
    @JsonPropertyDescription("Timestamp when upload completed")
    public Date uploadCompletedAt;

    @JsonProperty("upload_error_message")
    @JsonPropertyDescription("Error message from upload, if any")
    public String uploadErrorMessage;

    @JsonProperty("restart_database_after_migration")
    @JsonPropertyDescription("Whether the database needs to be restarted after migration")
    public boolean restartDatabaseAfterMigration;

    public Date getCreatedAt() {
        return createdAt;
    }

    @JsonIgnore
    public boolean isInstanceCreationSuccessful() {
        return instanceCreationCompletedAt != null && StringUtils.isEmpty(instanceCreationErrorMessage);
    }

    @JsonIgnore
    public boolean isUploadPending() {
        return isInstanceCreationSuccessful() && uploadStartedAt == null;
    }

    @JsonIgnore
    public boolean isDumpSuccessful() {
        return dumpCompletedAt != null
                && dumpStartedAt != null
                && !dumpStartedAt.after(dumpCompletedAt)
                && StringUtils.isEmpty(dumpErrorMessage);
    }
}
