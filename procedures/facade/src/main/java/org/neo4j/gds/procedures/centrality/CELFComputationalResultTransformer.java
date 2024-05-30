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
package org.neo4j.gds.procedures.centrality;

import org.neo4j.gds.algorithms.NodePropertyWriteResult;
import org.neo4j.gds.algorithms.centrality.specificfields.CELFSpecificFields;
import org.neo4j.gds.procedures.centrality.celf.CELFWriteResult;

final class CELFComputationalResultTransformer {

    private CELFComputationalResultTransformer() {}

    static CELFWriteResult toWriteResult(
        NodePropertyWriteResult<CELFSpecificFields> mutateResult
    ) {
        return new CELFWriteResult(
            mutateResult.writeMillis(),
            mutateResult.nodePropertiesWritten(),
            mutateResult.computeMillis(),
            mutateResult.algorithmSpecificFields().totalSpread(),
            mutateResult.algorithmSpecificFields().nodeCount(),
            mutateResult.configuration().toMap()
        );
    }


}
