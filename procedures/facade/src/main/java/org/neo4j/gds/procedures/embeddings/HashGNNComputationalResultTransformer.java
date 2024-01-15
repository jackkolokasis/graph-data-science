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
package org.neo4j.gds.procedures.embeddings;

import org.neo4j.gds.algorithms.StreamComputationResult;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.embeddings.hashgnn.HashGNNResult;
import org.neo4j.gds.procedures.embeddings.hashgnn.HashGNNStreamResult;

import java.util.stream.LongStream;
import java.util.stream.Stream;

public class HashGNNComputationalResultTransformer {

    public static Stream<HashGNNStreamResult> toStreamResult(
        StreamComputationResult<HashGNNResult> computationResult
    ) {
        return computationResult.result().map(hashGNNResult -> {
            var graph = computationResult.graph();
            return LongStream
                .range(IdMap.START_NODE_ID, graph.nodeCount())
                .mapToObj(i -> new HashGNNStreamResult(
                    graph.toOriginalNodeId(i),
                    hashGNNResult.embeddings().doubleArrayValue(i)
                ));

        }).orElseGet(Stream::empty);
    }
    

}
