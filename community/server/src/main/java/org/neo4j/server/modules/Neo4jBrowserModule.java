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
package org.neo4j.server.modules;

import static org.neo4j.server.web.StaticContent.classpathStaticContent;
import static org.neo4j.server.web.StaticContent.jarStaticContent;

import java.nio.file.Files;
import java.nio.file.Path;
import org.neo4j.server.web.WebServer;

public class Neo4jBrowserModule implements ServerModule {
    private static final String DEFAULT_NEO4J_BROWSER_PATH = "/browser";
    private static final String DEFAULT_NEO4J_BROWSER_STATIC_WEB_CONTENT_LOCATION = "browser";

    private final WebServer webServer;
    private final String browserZipPath;

    public Neo4jBrowserModule(WebServer webServer, String browserZipPath) {
        this.webServer = webServer;
        this.browserZipPath = browserZipPath;
    }

    @Override
    public void start() {
        // Fallback to original loading mechanism if zip not found
        if (Files.exists(Path.of(browserZipPath))) {
            webServer.addStaticContent(jarStaticContent(browserZipPath), DEFAULT_NEO4J_BROWSER_PATH);
        } else {
            webServer.addStaticContent(
                    classpathStaticContent(DEFAULT_NEO4J_BROWSER_STATIC_WEB_CONTENT_LOCATION),
                    DEFAULT_NEO4J_BROWSER_PATH);
        }
    }

    @Override
    public void stop() {
        webServer.removeStaticContent(DEFAULT_NEO4J_BROWSER_PATH);
    }
}
