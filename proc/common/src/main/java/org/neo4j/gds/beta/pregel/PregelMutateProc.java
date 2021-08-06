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
package org.neo4j.gds.beta.pregel;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.MutatePropertyProc;
import org.neo4j.gds.core.write.NodeProperty;

import java.util.List;

public abstract class PregelMutateProc<
    ALGO extends Algorithm<ALGO, PregelResult>,
    CONFIG extends PregelProcedureConfig>
    extends MutatePropertyProc<ALGO, PregelResult, PregelMutateResult, CONFIG> {

    @Override
    protected List<NodeProperty> nodePropertyList(ComputationResult<ALGO, PregelResult, CONFIG> computationResult) {
        return PregelBaseProc.nodeProperties(computationResult, computationResult.config().mutateProperty());
    }
}
