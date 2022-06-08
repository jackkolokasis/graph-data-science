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
package org.neo4j.gds.ml.decisiontree;

import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;
import org.neo4j.gds.ml.models.Features;

import static org.neo4j.gds.mem.MemoryUsage.sizeOfInstance;
import static org.neo4j.gds.mem.MemoryUsage.sizeOfLongArray;

public class DecisionTreeClassifierTrainer extends DecisionTreeTrainer<Integer> {

    private final HugeLongArray allLabels;
    private final LocalIdMap classIdMap;

    public DecisionTreeClassifierTrainer(
        ImpurityCriterion impurityCriterion,
        Features features,
        HugeLongArray labels,
        LocalIdMap classIdMap,
        DecisionTreeTrainerConfig config,
        FeatureBagger featureBagger
    ) {
        super(
            features,
            config,
            impurityCriterion,
            featureBagger
        );
        this.classIdMap = classIdMap;

        assert labels.size() == features.size();
        this.allLabels = labels;
    }

    public static MemoryRange memoryEstimation(
        DecisionTreeTrainerConfig config,
        long numberOfTrainingSamples,
        int numberOfClasses
    ) {
        return MemoryRange.of(sizeOfInstance(DecisionTreeClassifierTrainer.class))
            .add(DecisionTreeTrainer.estimateTree(
                config,
                numberOfTrainingSamples,
                TreeNode.leafMemoryEstimation(Integer.class),
                GiniIndex.GiniImpurityData.memoryEstimation(numberOfClasses)
            ))
            .add(sizeOfLongArray(numberOfClasses));
    }

    @Override
    protected Integer toTerminal(Group group) {
        final var classesInGroup = new long[classIdMap.size()];
        var array = group.array();

        for (long i = group.startIdx(); i < group.startIdx() + group.size(); i++) {
            long label = allLabels.get(array.get(i));
            classesInGroup[classIdMap.toMapped(label)]++;
        }

        long maxClassCountInGroup = -1;
        int maxMappedClass = 0;
        for (int i = 0; i < classesInGroup.length; i++) {
            if (classesInGroup[i] <= maxClassCountInGroup) continue;

            maxClassCountInGroup = classesInGroup[i];
            maxMappedClass = i;
        }

        return maxMappedClass;
    }
}