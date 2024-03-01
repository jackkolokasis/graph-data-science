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
package org.neo4j.gds.core.huge;

import org.immutables.builder.Builder;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.AdjacencyCursor;
import org.neo4j.gds.api.AdjacencyList;
import org.neo4j.gds.api.AdjacencyProperties;
import org.neo4j.gds.api.CSRGraph;
import org.neo4j.gds.api.FilteredIdMap;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphCharacteristics;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.ImmutableProperties;
import org.neo4j.gds.api.ImmutableTopology;
import org.neo4j.gds.api.Properties;
import org.neo4j.gds.api.PropertyCursor;
import org.neo4j.gds.api.RelationshipConsumer;
import org.neo4j.gds.api.RelationshipCursor;
import org.neo4j.gds.api.RelationshipWithPropertyConsumer;
import org.neo4j.gds.api.Topology;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.collections.primitive.PrimitiveLongIterable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

import java.lang.reflect.Field;

/**
 * Huge Graph contains two array like data structures.
 * <p>
 * The adjacency data is stored in a ByteArray, which is a byte[] addressable by
 * longs indices and capable of storing about 2^46 (~ 70k bn) bytes – or 64 TiB.
 * The bytes are stored in byte[] pages of 32 KiB size.
 * <p>
 * The data is in the format:
 * <blockquote>
 * <code>degree</code> ~ <code>targetId</code><sub><code>1</code></sub> ~ <code>targetId</code><sub><code>2</code></sub> ~ <code>targetId</code><sub><code>n</code></sub>
 * </blockquote>
 * The {@code degree} is stored as a fill-sized 4 byte long {@code int}
 * (the neo kernel api returns an int for {@link org.neo4j.internal.kernel.api.helpers.Nodes#countAll}).
 * Every target ID is first sorted, then delta encoded, and finally written as variable-length vlongs.
 * The delta encoding does not write the actual value but only the difference to the previous value, which plays very nice with the vlong encoding.
 * <p>
 * The seconds data structure is a LongArray, which is a long[] addressable by longs
 * and capable of storing about 2^43 (~9k bn) longs – or 64 TiB worth of 64 bit longs.
 * The data is the offset address into the aforementioned adjacency array, the index is the respective source node id.
 * <p>
 * To traverse all nodes, first access to offset from the LongArray, then read
 * 4 bytes into the {@code degree} from the ByteArray, starting from the offset, then read
 * {@code degree} vlongs as targetId.
 * <p>
 * Reading the degree from the offset position not only does not require the offset array
 * to be sorted but also allows the adjacency array to be sparse. This fact is
 * used during the import – each thread pre-allocates a local chunk of some pages (512 KiB)
 * and gives access to this data during import. Synchronization between threads only
 * has to happen when a new chunk has to be pre-allocated. This is similar to
 * what most garbage collectors do with TLAB allocations.
 *
 * @see <a href="https://developers.google.com/protocol-buffers/docs/encoding#varints">more abount vlong</a>
 * @see <a href="https://shipilev.net/jvm-anatomy-park/4-tlab-allocation/">more abount TLAB allocation</a>
 */
@Value.Style(typeBuilder = "HugeGraphBuilder")
public class HugeGraph implements CSRGraph {

    static final double NO_PROPERTY_VALUE = Double.NaN;

    protected final IdMap idMap;

    protected final GraphSchema schema;
    protected final GraphCharacteristics characteristics;

    protected final Map<String, NodePropertyValues> nodeProperties;

    protected final long relationshipCount;

    protected AdjacencyList adjacency;
    protected @Nullable AdjacencyList inverseAdjacency;

    private final double defaultPropertyValue;
    protected @Nullable AdjacencyProperties properties;
    protected @Nullable AdjacencyProperties inverseProperties;

    private AdjacencyCursor adjacencyCursorCache;
    private @Nullable AdjacencyCursor inverseAdjacencyCursorCache;
    private @Nullable PropertyCursor propertyCursorCache;
    private @Nullable PropertyCursor inversePropertyCursorCache;

    protected final boolean hasRelationshipProperty;

    private static final sun.misc.Unsafe _UNSAFE;

    static {
      try {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        _UNSAFE = (sun.misc.Unsafe) unsafeField.get(null);
      } catch (Exception e) {
        throw new RuntimeException("HugeHeap: Failed to " + "get unsafe", e);
      }
    }

    protected final boolean isMultiGraph;

    @Builder.Factory
    static HugeGraph create(
        IdMap nodes,
        GraphSchema schema,
        GraphCharacteristics characteristics,
        Map<String, NodePropertyValues> nodeProperties,
        Topology topology,
        Optional<Properties> relationshipProperties,
        Optional<Topology> inverseTopology,
        Optional<Properties> inverseRelationshipProperties
    ) {
        HugeGraph hugeGraph = new HugeGraph(
            nodes,
            schema,
            characteristics,
            nodeProperties,
            topology.elementCount(),
            topology.adjacencyList(),
            inverseTopology.map(Topology::adjacencyList).orElse(null),
            relationshipProperties.isPresent(),
            relationshipProperties.map(Properties::defaultPropertyValue).orElse(Double.NaN),
            relationshipProperties.map(Properties::propertiesList).orElse(null),
            inverseRelationshipProperties.map(Properties::propertiesList).orElse(null),
            topology.isMultiGraph()
        );
        
        _UNSAFE.h2Move(0);
        return hugeGraph;   
    }

    protected HugeGraph(
        IdMap idMap,
        GraphSchema schema,
        GraphCharacteristics characteristics,
        Map<String, NodePropertyValues> nodeProperties,
        long relationshipCount,
        @NotNull AdjacencyList adjacency,
        @Nullable AdjacencyList inverseAdjacency,
        boolean hasRelationshipProperty,
        double defaultRelationshipPropertyValue,
        @Nullable AdjacencyProperties relationshipProperty,
        @Nullable AdjacencyProperties inverseRelationshipProperty,
        boolean isMultiGraph
    ) {
        this.idMap = idMap;
        this.schema = schema;
        this.characteristics = characteristics;
        this.isMultiGraph = isMultiGraph;
        this.nodeProperties = nodeProperties;
        this.relationshipCount = relationshipCount;
        this.adjacency = adjacency;
        this.inverseAdjacency = inverseAdjacency;
        this.defaultPropertyValue = defaultRelationshipPropertyValue;
        this.properties = relationshipProperty;
        this.inverseProperties = inverseRelationshipProperty;
        this.hasRelationshipProperty = hasRelationshipProperty;

        this.adjacencyCursorCache = adjacency.rawAdjacencyCursor();
        this.inverseAdjacencyCursorCache = inverseAdjacency != null ? inverseAdjacency.rawAdjacencyCursor() : null;
        this.propertyCursorCache = relationshipProperty != null ? relationshipProperty.rawPropertyCursor() : null;
        this.inversePropertyCursorCache = inverseRelationshipProperty != null ? inverseRelationshipProperty.rawPropertyCursor() : null;
    }

    @Override
    public long nodeCount() {
        return idMap.nodeCount();
    }

    @Override
    public long nodeCount(NodeLabel nodeLabel) {
        return idMap.nodeCount(nodeLabel);
    }

    @Override
    public OptionalLong rootNodeCount() {
        return idMap.rootNodeCount();
    }

    @Override
    public long highestOriginalId() {
        return idMap.highestOriginalId();
    }

    public IdMap idMap() {
        return idMap;
    }

    @Override
    public IdMap rootIdMap() {
        return idMap.rootIdMap();
    }

    @Override
    public GraphSchema schema() {
        return schema;
    }

    @Override
    public GraphCharacteristics characteristics() {
        return characteristics;
    }

    public Map<String, NodePropertyValues> nodeProperties() {return nodeProperties;}

    @Override
    public long relationshipCount() {
        return relationshipCount;
    }

    @Override
    public Collection<PrimitiveLongIterable> batchIterables(long batchSize) {
        return idMap.batchIterables(batchSize);
    }

    @Override
    public void forEachNode(LongPredicate consumer) {
        idMap.forEachNode(consumer);
    }

    @Override
    public PrimitiveIterator.OfLong nodeIterator() {
        return idMap.nodeIterator();
    }

    @Override
    public PrimitiveIterator.OfLong nodeIterator(Set<NodeLabel> labels) {
        return idMap.nodeIterator(labels);
    }

    @Override
    public double relationshipProperty(long sourceNodeId, long targetNodeId) {
        return relationshipProperty(sourceNodeId, targetNodeId, defaultPropertyValue);
    }

    @Override
    public double relationshipProperty(long sourceId, long targetId, double fallbackValue) {
        if (!hasRelationshipProperty) {
            return fallbackValue;
        }

        double maybeValue;

        if (properties != null) {
            maybeValue = findPropertyValue(sourceId, targetId);
            if (!Double.isNaN(maybeValue)) {
                return maybeValue;
            }
        }

        return defaultPropertyValue;
    }

    private double findPropertyValue(long fromId, long toId) {
        var properties = Objects.requireNonNull(this.properties);

        var adjacencyCursor = adjacency.adjacencyCursor(fromId);
        if (!adjacencyCursor.hasNextVLong()) {
            return NO_PROPERTY_VALUE;
        }

        var propertyCursor = properties.propertyCursor(fromId, defaultPropertyValue);

        while (adjacencyCursor.hasNextVLong() && propertyCursor.hasNextLong() && adjacencyCursor.nextVLong() != toId) {
            propertyCursor.nextLong();
        }

        if (!propertyCursor.hasNextLong()) {
            return NO_PROPERTY_VALUE;
        }

        long doubleBits = propertyCursor.nextLong();
        return Double.longBitsToDouble(doubleBits);
    }

    @Override
    public NodePropertyValues nodeProperties(String propertyKey) {
        return nodeProperties.get(propertyKey);
    }

    @Override
    public Set<String> availableNodeProperties() {
        return nodeProperties.keySet();
    }

    @Override
    public void forEachRelationship(long nodeId, RelationshipConsumer consumer) {
        runForEach(nodeId, consumer);
    }

    @Override
    public void forEachRelationship(long nodeId, double fallbackValue, RelationshipWithPropertyConsumer consumer) {
        runForEach(nodeId, fallbackValue, consumer);
    }

    @Override
    public void forEachInverseRelationship(long nodeId, RelationshipConsumer consumer) {
        runForEachInverse(nodeId, consumer);
    }

    @Override
    public void forEachInverseRelationship(
        long nodeId,
        double fallbackValue,
        RelationshipWithPropertyConsumer consumer
    ) {
        runForEachInverse(nodeId, fallbackValue, consumer);
    }

    @Override
    public Stream<RelationshipCursor> streamRelationships(long nodeId, double fallbackValue) {
        var adjacencyCursor = adjacencyCursorForIteration(nodeId);
        var spliterator = !hasRelationshipProperty()
            ? AdjacencySpliterator.of(adjacencyCursor, nodeId, fallbackValue)
            : AdjacencySpliterator.of(adjacencyCursor, propertyCursorForIteration(nodeId), nodeId);

        return StreamSupport.stream(spliterator, false);
    }

    @Override
    public Graph relationshipTypeFilteredGraph(Set<RelationshipType> relationshipTypes) {
        assertSupportedRelationships(relationshipTypes);
        return this;
    }

    @Override
    public Map<RelationshipType, Topology> relationshipTopologies() {
        return Map.of(relationshipType(), relationshipTopology());
    }

    private void assertSupportedRelationships(Set<RelationshipType> relationshipTypes) {
        if (!relationshipTypes.isEmpty() && (relationshipTypes.size() > 1 || !relationshipTypes.contains(
            relationshipType()))) {
            throw new IllegalArgumentException(formatWithLocale(
                "One or more relationship types of %s in are not supported. This graph has a relationship of type %s.",
                relationshipTypes,
                relationshipType()
            ));
        }
    }

    private RelationshipType relationshipType() {
        return schema().relationshipSchema().availableTypes().iterator().next();
    }

    @Override
    public int degree(long node) {
        return adjacency.degree(node);
    }

    @Override
    public int degreeInverse(long nodeId) {
        if (inverseAdjacency == null) {
            throw new UnsupportedOperationException(
                "Cannot get inverse degree on a graph without inverse indexed relationships"
            );
        }
        return inverseAdjacency.degree(nodeId);
    }

    @Override
    public int degreeWithoutParallelRelationships(long nodeId) {
        if (!isMultiGraph()) {
            return degree(nodeId);
        }
        var degreeCounter = new ParallelRelationshipsDegreeCounter();
        runForEach(nodeId, degreeCounter);
        return degreeCounter.degree;
    }

    @Override
    public long toMappedNodeId(long originalNodeId) {
        return idMap.toMappedNodeId(originalNodeId);
    }

    @Override
    public String typeId() {
        return idMap.typeId();
    }

    @Override
    public long toOriginalNodeId(long mappedNodeId) {
        return idMap.toOriginalNodeId(mappedNodeId);
    }

    @Override
    public long toRootNodeId(long mappedNodeId) {
        return idMap.toRootNodeId(mappedNodeId);
    }

    @Override
    public boolean containsOriginalId(long originalNodeId) {
        return idMap.containsOriginalId(originalNodeId);
    }

    @Override
    public HugeGraph concurrentCopy() {
        return new HugeGraph(
            idMap,
            schema,
            characteristics,
            nodeProperties,
            relationshipCount,
            adjacency,
            inverseAdjacency,
            hasRelationshipProperty,
            defaultPropertyValue,
            properties,
            inverseProperties,
            isMultiGraph
        );
    }

    @Override
    public Optional<NodeFilteredGraph> asNodeFilteredGraph() {
        return Optional.empty();
    }

    /**
     * O(n) !
     */
    @Override
    public boolean exists(long sourceNodeId, long targetNodeId) {
        var cursor = adjacencyCursorForIteration(sourceNodeId);
        return cursor.advance(targetNodeId) == targetNodeId;
    }

    @Override
    public long nthTarget(long nodeId, int offset) {
        if (offset >= degree(nodeId)) {
            return NOT_FOUND;
        }

        var cursor = adjacencyCursorForIteration(nodeId);
        return cursor.advanceBy(offset);
    }

    private void runForEach(long sourceId, RelationshipConsumer consumer) {
        var adjacencyCursor = adjacencyCursorForIteration(sourceId);
        consumeAdjacentNodes(sourceId, adjacencyCursor, consumer);
    }

    private void runForEach(long sourceId, double fallbackValue, RelationshipWithPropertyConsumer consumer) {
        if (!hasRelationshipProperty()) {
            runForEach(sourceId, (s, t) -> consumer.accept(s, t, fallbackValue));
        } else {
            var adjacencyCursor = adjacencyCursorForIteration(sourceId);
            var propertyCursor = propertyCursorForIteration(sourceId);
            consumeAdjacentNodesWithProperty(sourceId, adjacencyCursor, propertyCursor, consumer);
        }
    }

    private void runForEachInverse(long sourceId, RelationshipConsumer consumer) {
        var adjacencyCursor = inverseAdjacencyCursorForIteration(sourceId);
        consumeAdjacentNodes(sourceId, adjacencyCursor, consumer);
    }

    private void runForEachInverse(long sourceId, double fallbackValue, RelationshipWithPropertyConsumer consumer) {
        if (!hasRelationshipProperty()) {
            runForEachInverse(sourceId, (s, t) -> consumer.accept(s, t, fallbackValue));
        } else {
            var adjacencyCursor = inverseAdjacencyCursorForIteration(sourceId);
            var propertyCursor = inversePropertyCursorForIteration(sourceId);
            consumeAdjacentNodesWithProperty(sourceId, adjacencyCursor, propertyCursor, consumer);
        }
    }

    private AdjacencyCursor adjacencyCursorForIteration(long sourceNodeId) {
        return adjacency.adjacencyCursor(adjacencyCursorCache, sourceNodeId);
    }

    private PropertyCursor propertyCursorForIteration(long sourceNodeId) {
        if (!hasRelationshipProperty() || properties == null) {
            throw new UnsupportedOperationException(
                "Cannot create property cursor on a graph without relationship property"
            );
        }

        return properties.propertyCursor(propertyCursorCache, sourceNodeId, defaultPropertyValue);
    }

    private AdjacencyCursor inverseAdjacencyCursorForIteration(long sourceNodeId) {
        if (inverseAdjacency == null) {
            throw new UnsupportedOperationException(
                "Cannot create adjacency cursor on a graph without inverse indexed relationships"
            );
        }

        return inverseAdjacency.adjacencyCursor(inverseAdjacencyCursorCache, sourceNodeId);
    }

    private PropertyCursor inversePropertyCursorForIteration(long sourceNodeId) {
        if (!hasRelationshipProperty() || inverseProperties == null) {
            throw new UnsupportedOperationException(
                "Cannot create property cursor on a graph without relationship property"
            );
        }

        return inverseProperties.propertyCursor(inversePropertyCursorCache, sourceNodeId, defaultPropertyValue);
    }


    @Override
    public boolean isMultiGraph() {
        return isMultiGraph;
    }

    @Override
    public boolean hasRelationshipProperty() {
        return hasRelationshipProperty;
    }

    public Topology relationshipTopology() {
        return ImmutableTopology.of(
            adjacency,
            relationshipCount,
            isMultiGraph()
        );
    }

    public Optional<Topology> inverseRelationshipTopology() {
        return Optional.ofNullable(inverseAdjacency).map(adjacencyList -> ImmutableTopology.of(
            adjacency,
            relationshipCount,
            isMultiGraph()
        ));
    }

    public Optional<Properties> relationshipProperties() {
        return Optional
            .ofNullable(properties)
            .map(properties -> ImmutableProperties.of(properties, relationshipCount, defaultPropertyValue));
    }

    public Optional<Properties> inverseRelationshipProperties() {
        return Optional
            .ofNullable(inverseProperties)
            .map(properties -> ImmutableProperties.of(properties, relationshipCount, defaultPropertyValue));
    }

    private void consumeAdjacentNodes(
        long sourceId,
        AdjacencyCursor adjacencyCursor,
        RelationshipConsumer consumer
    ) {
        while (adjacencyCursor.hasNextVLong()) {
            if (!consumer.accept(sourceId, adjacencyCursor.nextVLong())) {
                break;
            }
        }
    }

    private void consumeAdjacentNodesWithProperty(
        long sourceId,
        AdjacencyCursor adjacencyCursor,
        PropertyCursor propertyCursor,
        RelationshipWithPropertyConsumer consumer
    ) {

        while (adjacencyCursor.hasNextVLong()) {
            long targetId = adjacencyCursor.nextVLong();

            long propertyBits = propertyCursor.nextLong();
            double property = Double.longBitsToDouble(propertyBits);

            if (!consumer.accept(sourceId, targetId, property)) {
                break;
            }
        }
    }

    @Override
    public List<NodeLabel> nodeLabels(long mappedNodeId) {
        return idMap.nodeLabels(mappedNodeId);
    }

    @Override
    public void forEachNodeLabel(long mappedNodeId, NodeLabelConsumer consumer) {
        idMap.forEachNodeLabel(mappedNodeId, consumer);
    }

    @Override
    public Set<NodeLabel> availableNodeLabels() {
        return idMap.availableNodeLabels();
    }

    @Override
    public boolean hasLabel(long mappedNodeId, NodeLabel label) {
        return idMap.hasLabel(mappedNodeId, label);
    }

    @Override
    public Optional<FilteredIdMap> withFilteredLabels(Collection<NodeLabel> nodeLabels, int concurrency) {
        return idMap.withFilteredLabels(nodeLabels, concurrency);
    }

    private static class ParallelRelationshipsDegreeCounter implements RelationshipConsumer {
        private long previousNodeId;
        private int degree;

        ParallelRelationshipsDegreeCounter() {
            this.previousNodeId = -1;
        }

        @Override
        public boolean accept(long s, long t) {
            if (t != previousNodeId) {
                degree++;
                previousNodeId = t;
            }
            return true;
        }
    }

    public void addNodeLabel(NodeLabel nodeLabel) {
        idMap.addNodeLabel(nodeLabel);
    }

    public void addNodeIdToLabel(long nodeId, NodeLabel nodeLabel) {
        idMap.addNodeIdToLabel(nodeId, nodeLabel);
    }
}
