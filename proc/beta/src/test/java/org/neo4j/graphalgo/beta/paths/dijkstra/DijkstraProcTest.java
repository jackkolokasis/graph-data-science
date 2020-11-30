/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.beta.paths.dijkstra;

import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.graphalgo.AlgoBaseProcTest;
import org.neo4j.graphalgo.BaseProcTest;
import org.neo4j.graphalgo.HeapControlTest;
import org.neo4j.graphalgo.RelationshipWeightConfigTest;
import org.neo4j.graphalgo.catalog.GraphCreateProc;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphStoreCatalog;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.beta.paths.ShortestPathBaseConfig.SOURCE_NODE_KEY;
import static org.neo4j.graphalgo.beta.paths.ShortestPathBaseConfig.TARGET_NODE_KEY;

abstract class DijkstraProcTest<CONFIG extends DijkstraBaseConfig> extends BaseProcTest implements
    AlgoBaseProcTest<Dijkstra, CONFIG, DijkstraResult>,
//    MemoryEstimateTest<Dijkstra, CONFIG, DijkstraResult>,
    HeapControlTest<Dijkstra, CONFIG, DijkstraResult>,
    RelationshipWeightConfigTest<Dijkstra, CONFIG, DijkstraResult>
{

    @Override
    public String createQuery() {
        return "CREATE" +
               "  (a:Label { prop: 0 })" +
               ", (b:Label)" +
               ", (c:Label)" +
               ", (d:Label)" +
               ", (e:Label)" +
               ", (f:Label { prop: 1 })" +
               ", (a)-[:TYPE {cost: 4}]->(b)" +
               ", (a)-[:TYPE {cost: 2}]->(c)" +
               ", (b)-[:TYPE {cost: 5}]->(c)" +
               ", (b)-[:TYPE {cost: 10}]->(d)" +
               ", (c)-[:TYPE {cost: 3}]->(e)" +
               ", (d)-[:TYPE {cost: 11}]->(f)" +
               ", (e)-[:TYPE {cost: 4}]->(d)";
    }

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(
            getProcedureClazz(),
            GraphCreateProc.class
        );
        runQuery(createQuery());
    }

    @AfterEach
    void teardown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Override
    public GraphDatabaseAPI graphDb() {
        return db;
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        var sourceId = new MutableLong(0L);
        var targetId = new MutableLong(0L);

        runQueryWithRowConsumer(
            "MATCH (n) WHERE n.prop = 0 RETURN id(n) AS source",
            resultRow -> sourceId.setValue(resultRow.getNumber("source"))
        );
        runQueryWithRowConsumer(
            "MATCH (n) WHERE n.prop = 0 RETURN id(n) AS target",
            resultRow -> targetId.setValue(resultRow.getNumber("target"))
        );

        if (!mapWrapper.containsKey(SOURCE_NODE_KEY)) {
            mapWrapper = mapWrapper.withNumber(SOURCE_NODE_KEY, sourceId.longValue());
        }
        if (!mapWrapper.containsKey(TARGET_NODE_KEY)) {
            mapWrapper = mapWrapper.withNumber(TARGET_NODE_KEY, targetId.longValue());
        }
        return mapWrapper;
    }

    @Override
    public void assertResultEquals(DijkstraResult result1, DijkstraResult result2) {
        assertEquals(result1.pathSet(), result2.pathSet());
    }
}
