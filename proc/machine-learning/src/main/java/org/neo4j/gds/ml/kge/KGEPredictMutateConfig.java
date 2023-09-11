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
package org.neo4j.gds.ml.kge;

import org.immutables.value.Value;
import org.neo4j.gds.ElementProjection;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.ElementTypeValidator;
import org.neo4j.gds.config.MutateRelationshipConfig;
import org.neo4j.gds.core.CypherMapWrapper;

import java.util.Collection;
import java.util.List;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@ValueClass
@Configuration
public interface KGEPredictMutateConfig extends MutateRelationshipConfig, AlgoBaseConfig {


    @Value.Default
    default String sourceNodeLabel() {
        return ElementProjection.PROJECT_ALL;
    }

    @Value.Default
    default String targetNodeLabel() {
        return ElementProjection.PROJECT_ALL;
    }

    String nodeEmbeddingProperty();

    //TODO use HugeList or double[]
    List<Double> relationshipTypeEmbedding();

    String scoringFunction();

    @Configuration.IntegerRange(min = 1)
    int topK();

    static KGEPredictMutateConfig of(CypherMapWrapper userInput) {
        return new KGEPredictMutateConfigImpl(userInput);
    }

    @Configuration.GraphStoreValidationCheck
    default void validateSourceNodeLabel(
        GraphStore graphStore,
        Collection<NodeLabel> selectedLabels,
        Collection<RelationshipType> selectedRelationshipTypes
    ) {
        ElementTypeValidator.resolveAndValidate(graphStore, List.of(sourceNodeLabel()), "`sourceNodeLabel`");
    }

    @Configuration.GraphStoreValidationCheck
    default void validateTargetNodeLabel(
        GraphStore graphStore,
        Collection<NodeLabel> selectedLabels,
        Collection<RelationshipType> selectedRelationshipTypes
    ) {
        ElementTypeValidator.resolveAndValidate(graphStore, List.of(targetNodeLabel()), "`targetNodeLabel`");
    }

    @Value.Check
    default void validateScoringFunction() {
        if (!(scoringFunction().equalsIgnoreCase("transE")
            || scoringFunction().equalsIgnoreCase("distMult"))) {
            throw new IllegalArgumentException(formatWithLocale(
                "Invalid scoring function %s, it needs to be either TransE or DistMult.", scoringFunction()
            ));
        }
    }


}
