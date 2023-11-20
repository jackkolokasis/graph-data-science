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
package org.neo4j.gds.paths.spanningtree;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.executor.MemoryEstimationExecutor;
import org.neo4j.gds.executor.ProcedureExecutor;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class SpanningTreeMutateProc extends BaseProc {
    static final String procedure = "gds.spanningTree.mutate";
    static final String betaProcedure = "gds.beta.spanningTree.mutate";

    static final String DESCRIPTION = SpanningTreeWriteProc.DESCRIPTION;

    @Procedure(value = procedure, mode = READ)
    @Description(DESCRIPTION)
    public Stream<MutateResult> spanningTree(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return new ProcedureExecutor<>(
            new SpanningTreeMutateSpec(),
            executionContext()
        ).compute(graphName, configuration);
    }

    @Procedure(value = procedure + ".estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphName,
        @Name(value = "algoConfiguration") Map<String, Object> configuration
    ) {
        var spec = new SpanningTreeMutateSpec();
        return new MemoryEstimationExecutor<>(
            spec,
            executionContext(),
            transactionContext()
        ).computeEstimate(graphName, configuration);
    }

    @Procedure(value = betaProcedure, mode = READ, deprecatedBy = procedure)
    @Description(DESCRIPTION)
    @Internal
    @Deprecated
    public Stream<MutateResult> betaSpanningTree(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        executionContext()
            .metricsFacade()
            .deprecatedProcedures().called(betaProcedure);

        executionContext()
            .log()
            .warn("Procedure `gds.beta.spanningTree.mutate` has been deprecated, please use `gds.spanningTree.mutate`.");
        return spanningTree(graphName, configuration);
    }

    @Procedure(value = betaProcedure + ".estimate", mode = READ, deprecatedBy = procedure + ".estimate")
    @Description(ESTIMATE_DESCRIPTION)
    @Internal
    @Deprecated
    public Stream<MemoryEstimateResult> betaEstimate(
        @Name(value = "graphNameOrConfiguration") Object graphName,
        @Name(value = "algoConfiguration") Map<String, Object> configuration
    ) {
        executionContext()
            .metricsFacade()
            .deprecatedProcedures().called(betaProcedure + ".estimate");
        executionContext()
            .log()
            .warn(
                "Procedure `gds.beta.spanningTree.mutate.estimate` has been deprecated, please use `gds.spanningTree.mutate.estimate`.");
        return estimate(graphName, configuration);
    }


}
