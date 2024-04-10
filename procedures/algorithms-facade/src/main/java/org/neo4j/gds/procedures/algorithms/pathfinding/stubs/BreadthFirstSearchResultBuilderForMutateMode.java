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
package org.neo4j.gds.procedures.algorithms.pathfinding.stubs;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.machinery.AlgorithmProcessingTimings;
import org.neo4j.gds.applications.algorithms.machinery.ResultBuilder;
import org.neo4j.gds.applications.algorithms.machinery.SideEffectProcessingCounts;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.paths.traverse.BfsMutateConfig;
import org.neo4j.gds.procedures.algorithms.pathfinding.PathFindingMutateResult;

import java.util.Optional;

class BreadthFirstSearchResultBuilderForMutateMode implements ResultBuilder<BfsMutateConfig, HugeLongArray, PathFindingMutateResult> {
    @Override
    public PathFindingMutateResult build(
        Graph graph,
        GraphStore graphStore,
        BfsMutateConfig configuration,
        Optional<HugeLongArray> result,
        AlgorithmProcessingTimings timings,
        SideEffectProcessingCounts counts
    ) {
        return new PathFindingMutateResult.Builder()
            .withConfig(configuration)
            .withPreProcessingMillis(timings.preProcessingMillis)
            .withComputeMillis(timings.computeMillis)
            .withMutateMillis(timings.postProcessingMillis)
            .withRelationshipsWritten(counts.relationshipsWritten)
            .build();
    }
}