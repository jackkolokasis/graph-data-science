/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.procedures.catalog;

public final class GraphCatalogProcedureConstants {
    public static final String DROP_DESCRIPTION = "Drops a named graph from the catalog and frees up the resources it occupies.";
    public static final String DROP_GRAPH_PROPERTY_DESCRIPTION = "Removes a graph property from a projected graph.";
    public static final String STREAM_GRAPH_PROPERTY_DESCRIPTION = "Streams the given graph property.";
    public static final String DROP_NODE_PROPERTIES_DESCRIPTION = "Removes node properties from a projected graph.";
    public static final String DROP_RELATIONSHIPS_DESCRIPTION = "Delete the relationship type for a given graph stored in the graph-catalog.";
    public static final String EXISTS_DESCRIPTION = "Checks if a graph exists in the catalog.";
    public static final String LIST_DESCRIPTION = "Lists information about named graphs stored in the catalog.";
    public static final String PROJECT_DESCRIPTION = "Creates a named graph in the catalog for use by algorithms.";
    public static final String STREAM_NODE_PROPERTIES_DESCRIPTION = "Streams the given node properties.";
    public static final String STREAM_NODE_PROPERTY_DESCRIPTION = "Streams the given node property.";

    private GraphCatalogProcedureConstants() {}
}