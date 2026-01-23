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
package org.neo4j.fleetmanagement.configuration;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class State {
    private final PropertyChangeSupport changeSupport;
    private Boolean active = null; // Set this to null to fire change event on first fetch
    private boolean connected;
    private boolean rotatingToken;
    private String connectionMessage;
    private boolean topologyInitialized;
    public static final String ACTIVE_CHANGE = "active";
    public static final String CONNECTED_CHANGE = "connected";
    public static final String ROTATING_TOKEN_CHANGE = "rotatingToken";
    public static final String TOPOLOGY_INITIALIZED = "topologyInitialized";

    public State() {
        this.changeSupport = new PropertyChangeSupport(this);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListeners() {
        for (var listener : changeSupport.getPropertyChangeListeners()) {
            changeSupport.removePropertyChangeListener(listener);
        }
    }

    public void setActive(boolean active) {
        changeSupport.firePropertyChange(ACTIVE_CHANGE, this.active, (Object) active);
        this.active = active;
        if (!active) {
            this.connectionMessage = null;
        }
    }

    private void setConnected(boolean connected) {
        changeSupport.firePropertyChange(CONNECTED_CHANGE, this.connected, connected);
        this.connected = connected;
        if (connected) {
            this.connectionMessage = null;
        }
    }

    public void setConnected() {
        this.connectionMessage = null;
        this.setConnected(true);
    }

    public void setDisconnected(String errorMessage) {
        this.connectionMessage = errorMessage;
        this.setConnected(false);
        this.setTopologyInitialized(false);
    }

    public void setConnectionMessage(String connectionMessage) {
        this.connectionMessage = connectionMessage;
    }

    public void setRotatingToken(boolean rotatingToken) {
        changeSupport.firePropertyChange(ROTATING_TOKEN_CHANGE, this.rotatingToken, rotatingToken);
        this.rotatingToken = rotatingToken;
    }

    public void setTopologyInitialized(boolean isTopologyInitialized) {
        changeSupport.firePropertyChange(TOPOLOGY_INITIALIZED, this.topologyInitialized, isTopologyInitialized);
        this.topologyInitialized = isTopologyInitialized;
    }

    public boolean isConnected() {
        return this.connected;
    }

    public boolean isActive() {
        if (this.active == null) {
            return false;
        }
        return this.active;
    }

    public boolean isRotatingToken() {
        return this.rotatingToken;
    }

    public boolean isTopologyInitialized() {
        return this.topologyInitialized;
    }

    public String getConnectionMessage() {
        return this.connectionMessage;
    }
}
