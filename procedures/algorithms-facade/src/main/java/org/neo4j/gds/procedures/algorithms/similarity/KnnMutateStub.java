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
package org.neo4j.gds.procedures.algorithms.similarity;

import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.applications.ApplicationsFacade;
import org.neo4j.gds.applications.algorithms.similarity.SimilarityAlgorithmsEstimationModeBusinessFacade;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.procedures.algorithms.stubs.GenericStub;
import org.neo4j.gds.procedures.algorithms.stubs.MutateStub;
import org.neo4j.gds.applications.algorithms.machinery.MemoryEstimateResult;
import org.neo4j.gds.similarity.knn.KnnMutateConfig;

import java.util.Map;
import java.util.stream.Stream;

public class KnnMutateStub implements MutateStub<KnnMutateConfig, KnnMutateResult> {
    private final GenericStub genericStub;
    private final ApplicationsFacade applicationsFacade;
    private final ProcedureReturnColumns procedureReturnColumns;

    KnnMutateStub(
        GenericStub genericStub,
        ApplicationsFacade applicationsFacade,
        ProcedureReturnColumns procedureReturnColumns
    ) {
        this.genericStub = genericStub;
        this.applicationsFacade = applicationsFacade;
        this.procedureReturnColumns = procedureReturnColumns;
    }

    @Override
    public void validateConfiguration(Map<String, Object> configuration) {
        genericStub.validateConfiguration(KnnMutateConfig::of, configuration);
    }

    @Override
    public KnnMutateConfig parseConfiguration(Map<String, Object> configuration) {
        return genericStub.parseConfiguration(KnnMutateConfig::of, configuration);
    }

    @Override
    public MemoryEstimation getMemoryEstimation(String username, Map<String, Object> configuration) {
        return genericStub.getMemoryEstimation(
            username,
            configuration,
            KnnMutateConfig::of,
            estimationMode()::knn
        );
    }

    @Override
    public Stream<MemoryEstimateResult> estimate(Object graphName, Map<String, Object> configuration) {
        return genericStub.estimate(
            graphName,
            configuration,
            KnnMutateConfig::of,
            estimationMode()::knn
        );
    }

    @Override
    public Stream<KnnMutateResult> execute(String graphNameAsString, Map<String, Object> rawConfiguration) {
        var resultBuilder = new KnnResultBuilderForMutateMode();

        return genericStub.execute(
            graphNameAsString,
            rawConfiguration,
            KnnMutateConfig::of,
            (graphName, configuration, __) -> applicationsFacade.similarity().mutate().knn(
                graphName,
                configuration,
                resultBuilder,
                procedureReturnColumns.contains("similarityDistribution")
            ),
            resultBuilder
        );
    }

    private SimilarityAlgorithmsEstimationModeBusinessFacade estimationMode() {
        return applicationsFacade.similarity().estimate();
    }
}
