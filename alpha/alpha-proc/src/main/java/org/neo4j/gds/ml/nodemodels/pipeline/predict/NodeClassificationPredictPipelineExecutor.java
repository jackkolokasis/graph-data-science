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
package org.neo4j.gds.ml.nodemodels.pipeline.predict;


import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.ml.nodemodels.NodeClassificationPredict;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeClassificationResult;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionData;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionPredictor;
import org.neo4j.gds.ml.nodemodels.pipeline.NodeClassificationPipeline;
import org.neo4j.gds.ml.pipeline.ImmutableGraphFilter;
import org.neo4j.gds.ml.pipeline.PipelineExecutor;

import java.util.Map;
import java.util.Optional;

public class NodeClassificationPredictPipelineExecutor extends PipelineExecutor<
    NodeClassificationPredictPipelineBaseConfig,
    NodeClassificationPipeline,
    NodeClassificationResult
> {
    private static final int BATCH_SIZE = 100;
    private final NodeLogisticRegressionData modelData;

    NodeClassificationPredictPipelineExecutor(
        NodeClassificationPipeline pipeline,
        NodeClassificationPredictPipelineBaseConfig config,
        ExecutionContext executionContext,
        GraphStore graphStore,
        String graphName,
        ProgressTracker progressTracker,
        NodeLogisticRegressionData modelData
    ) {
        super(pipeline, config, executionContext, graphStore, graphName, progressTracker);
        this.modelData = modelData;
    }

    @Override
    public Map<DatasetSplits, GraphFilter> splitDataset() {
        return Map.of(
            DatasetSplits.FEATURE_INPUT,
            ImmutableGraphFilter.of(
                config.nodeLabelIdentifiers(graphStore),
                config.internalRelationshipTypes(graphStore)
            )
        );
    }

    @Override
    protected NodeClassificationResult execute(Map<DatasetSplits, GraphFilter> dataSplits) {
        var graph =graphStore.getGraph(
            config.nodeLabelIdentifiers(graphStore),
            config.internalRelationshipTypes(graphStore),
            Optional.empty()
        );
        var innerAlgo = new NodeClassificationPredict(
            new NodeLogisticRegressionPredictor(modelData, pipeline.featureProperties()),
            graph,
            BATCH_SIZE,
            config.concurrency(),
            config.includePredictedProbabilities(),
            pipeline.featureProperties(),
            executionContext.allocationTracker(),
            progressTracker
        );
        return innerAlgo.compute();
    }
}
