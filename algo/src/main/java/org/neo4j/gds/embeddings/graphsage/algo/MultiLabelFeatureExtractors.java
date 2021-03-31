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
package org.neo4j.gds.embeddings.graphsage.algo;

import org.neo4j.gds.ml.features.FeatureExtractor;
import org.neo4j.graphalgo.NodeLabel;

import java.util.List;
import java.util.Map;

public class MultiLabelFeatureExtractors {
    private final Map<NodeLabel, Integer> featureCountPerLabel;
    private final Map<NodeLabel, List<FeatureExtractor>> extractorsPerLabel;

    public MultiLabelFeatureExtractors(
        Map<NodeLabel, Integer> featureCountPerLabel,
        Map<NodeLabel, List<FeatureExtractor>> extractorsPerLabel
    ) {
        this.featureCountPerLabel = featureCountPerLabel;
        this.extractorsPerLabel = extractorsPerLabel;
    }

    public Map<NodeLabel, Integer> featureCountPerLabel() {
        return featureCountPerLabel;
    }

    public Map<NodeLabel, List<FeatureExtractor>> extractorsPerLabel() {
        return extractorsPerLabel;
    }
}
