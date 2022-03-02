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
package org.neo4j.gds.ml.nodemodels.logisticregression;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.ml.Predictor;
import org.neo4j.gds.ml.core.ComputationContext;
import org.neo4j.gds.ml.core.Variable;
import org.neo4j.gds.ml.core.batch.Batch;
import org.neo4j.gds.ml.core.features.BiasFeature;
import org.neo4j.gds.ml.core.features.FeatureExtraction;
import org.neo4j.gds.ml.core.features.FeatureExtractor;
import org.neo4j.gds.ml.core.functions.Constant;
import org.neo4j.gds.ml.core.functions.MatrixMultiplyWithTransposedSecondOperand;
import org.neo4j.gds.ml.core.functions.Softmax;
import org.neo4j.gds.ml.core.tensor.Matrix;

import java.util.ArrayList;
import java.util.List;

import static org.neo4j.gds.ml.core.features.FeatureExtraction.extract;

public class NodeLogisticRegressionPredictor implements Predictor<Matrix, NodeLogisticRegressionData> {

    private final NodeLogisticRegressionData modelData;
    private final List<String> featureProperties;


    public NodeLogisticRegressionPredictor(NodeLogisticRegressionData modelData, List<String> featureProperties) {
        this.modelData = modelData;
        this.featureProperties = featureProperties;
    }

    @Override
    public NodeLogisticRegressionData modelData() {
        return modelData;
    }

    @Override
    public Matrix predict(Graph graph, Batch batch) {
        ComputationContext ctx = new ComputationContext();
        return ctx.forward(predictionsVariable(graph, batch));
    }

    Variable<Matrix> predictionsVariable(Graph graph, Batch batch) {
        var features = features(graph, batch);
        var weights = modelData.weights();
        return new Softmax(MatrixMultiplyWithTransposedSecondOperand.of(features, weights));
    }

    private Constant<Matrix> features(Graph graph, Batch batch) {
        var featureExtractors = featureExtractors(graph);
        return extract(batch, featureExtractors);
    }

    private List<FeatureExtractor> featureExtractors(Graph graph) {
        var featureExtractors = new ArrayList<FeatureExtractor>();
        featureExtractors.addAll(FeatureExtraction.propertyExtractors(graph, featureProperties));
        featureExtractors.add(new BiasFeature());
        return featureExtractors;
    }
}
