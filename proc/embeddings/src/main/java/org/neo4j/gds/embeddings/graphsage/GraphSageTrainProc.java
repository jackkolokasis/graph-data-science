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
package org.neo4j.gds.embeddings.graphsage;

import org.neo4j.gds.procedures.GraphDataScienceProcedures;
import org.neo4j.gds.procedures.embeddings.graphsage.GraphSageTrainResult;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.ProcedureConstants.ESTIMATE_DESCRIPTION;
import static org.neo4j.gds.embeddings.graphsage.GraphSageCompanion.GRAPH_SAGE_DESCRIPTION;

public class GraphSageTrainProc {

    @Context
    public GraphDataScienceProcedures facade;

    @Description(GRAPH_SAGE_DESCRIPTION)
    @Procedure(name = "gds.beta.graphSage.train", mode = Mode.READ)
    public Stream<GraphSageTrainResult> train(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return facade.nodeEmbeddings().graphSage().train(graphName, configuration);
    }

    @Description(ESTIMATE_DESCRIPTION)
    @Procedure(name = "gds.beta.graphSage.train.estimate", mode = Mode.READ)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return facade.nodeEmbeddings().graphSage().trainEstimate(graphNameOrConfiguration, algoConfiguration);
    }
    
}
