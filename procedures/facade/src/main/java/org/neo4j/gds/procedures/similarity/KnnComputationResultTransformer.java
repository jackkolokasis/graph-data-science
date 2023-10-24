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
package org.neo4j.gds.procedures.similarity;

import org.neo4j.gds.algorithms.KnnSpecificFields;
import org.neo4j.gds.algorithms.StatsResult;
import org.neo4j.gds.algorithms.StreamComputationResult;
import org.neo4j.gds.procedures.similarity.knn.KnnStatsResult;
import org.neo4j.gds.similarity.SimilarityResult;
import org.neo4j.gds.similarity.knn.KnnResult;
import org.neo4j.gds.similarity.knn.KnnStatsConfig;

import java.util.stream.Stream;

final class KnnComputationResultTransformer {
    private KnnComputationResultTransformer() {}

    static Stream<SimilarityResult> toStreamResult(
        StreamComputationResult<KnnResult> computationResult
    ) {
        return computationResult.result().map(result -> {
            var graph = computationResult.graph();
            return result.streamSimilarityResult()
                .map(similarityResult -> {
                    similarityResult.node1 = graph.toOriginalNodeId(similarityResult.node1);
                    similarityResult.node2 = graph.toOriginalNodeId(similarityResult.node2);
                    return similarityResult;
                });
        }).orElseGet(Stream::empty);
    }

    static KnnStatsResult toStatsResult(
        StatsResult<KnnSpecificFields> statsResult,
        KnnStatsConfig config
    ) {

        return new KnnStatsResult(
            statsResult.preProcessingMillis(),
            statsResult.computeMillis(),
            statsResult.postProcessingMillis(),
            statsResult.algorithmSpecificFields().nodesCompared(),
            statsResult.algorithmSpecificFields().relationshipsWritten(),
            statsResult.algorithmSpecificFields().similarityDistribution(),
            statsResult.algorithmSpecificFields().didConverge(),
            statsResult.algorithmSpecificFields().ranIterations(),
            statsResult.algorithmSpecificFields().nodePairsConsidered(),
            config.toMap()
        );
    }




}
