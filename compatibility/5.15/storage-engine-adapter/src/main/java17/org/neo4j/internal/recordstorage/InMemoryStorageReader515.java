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
package org.neo4j.internal.recordstorage;

import org.eclipse.collections.api.set.primitive.IntSet;
import org.neo4j.common.EntityType;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.counts.CountsStore;
import org.neo4j.counts.CountsVisitor;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.compat._515.InMemoryNodeCursor;
import org.neo4j.gds.compat._515.InMemoryPropertyCursor;
import org.neo4j.gds.compat._515.InMemoryRelationshipScanCursor;
import org.neo4j.gds.compat._515.InMemoryRelationshipTraversalCursor;
import org.neo4j.gds.core.cypher.CypherGraphStore;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.internal.schema.constraints.IndexBackedConstraintDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.AllNodeScan;
import org.neo4j.storageengine.api.AllRelationshipsScan;
import org.neo4j.storageengine.api.StorageNodeCursor;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;
import org.neo4j.storageengine.api.StorageRelationshipTraversalCursor;
import org.neo4j.storageengine.api.StorageSchemaReader;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.token.TokenHolders;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class InMemoryStorageReader515 implements StorageReader {

    protected final CypherGraphStore graphStore;
    protected final TokenHolders tokenHolders;
    protected final CountsStore counts;
    private final Map<Class<?>, Object> dependantState;
    private boolean closed;

    public InMemoryStorageReader515(
        CypherGraphStore graphStore,
        TokenHolders tokenHolders,
        CountsStore counts
    ) {
        this.graphStore = graphStore;

        this.tokenHolders = tokenHolders;
        this.counts = counts;
        this.dependantState = new ConcurrentHashMap<>();
    }

    @Override
    public Collection<IndexBackedConstraintDescriptor> uniquenessConstraintsGetRelated(
        int[] changedLabels,
        int[] unchangedLabels,
        int[] propertyKeyIds,
        boolean propertyKeyListIsComplete,
        EntityType entityType
    ) {
        return Collections.emptyList();
    }

    @Override
    public long relationshipsGetCount(CursorContext cursorTracer) {
        return graphStore.relationshipCount();
    }

    @Override
    public boolean nodeExists(long id, StoreCursors storeCursors) {
        var originalId = graphStore.nodes().toOriginalNodeId(id);
        return graphStore.nodes().containsOriginalId(originalId);
    }

    @Override
    public boolean relationshipExists(long id, StoreCursors storeCursors) {
        return true;
    }

    @Override
    public StorageNodeCursor allocateNodeCursor(
        CursorContext cursorContext, StoreCursors storeCursors
    ) {
        return new InMemoryNodeCursor(graphStore, tokenHolders);
    }

    @Override
    public StoragePropertyCursor allocatePropertyCursor(
        CursorContext cursorContext, StoreCursors storeCursors, MemoryTracker memoryTracker
    ) {
        return new InMemoryPropertyCursor(graphStore, tokenHolders);
    }

    @Override
    public StorageRelationshipTraversalCursor allocateRelationshipTraversalCursor(
        CursorContext cursorContext, StoreCursors storeCursors
    ) {
        return new InMemoryRelationshipTraversalCursor(graphStore, tokenHolders);
    }

    @Override
    public StorageRelationshipScanCursor allocateRelationshipScanCursor(
        CursorContext cursorContext, StoreCursors storeCursors
    ) {
        return new InMemoryRelationshipScanCursor(graphStore, tokenHolders);
    }

    @Override
    public IndexDescriptor indexGetForSchemaAndType(
        SchemaDescriptor descriptor, IndexType type
    ) {
        return null;
    }

    @Override
    public AllRelationshipsScan allRelationshipScan() {
        return new AbstractInMemoryAllRelationshipScan() {
            @Override
            boolean scanRange(AbstractInMemoryRelationshipScanCursor cursor, long start, long stopInclusive) {
                return cursor.scanRange(start, stopInclusive);
            }

            @Override
            public boolean scanBatch(long sizeHint, AbstractInMemoryRelationshipScanCursor cursor) {
                return super.scanBatch(sizeHint, cursor);
            }
        };
    }

    @Override
    public Iterator<IndexDescriptor> indexGetForSchema(SchemaDescriptor descriptor) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetForLabel(int labelId) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetForRelationshipType(int relationshipType) {
        return Collections.emptyIterator();
    }

    @Override
    public IndexDescriptor indexGetForName(String name) {
        return null;
    }

    @Override
    public ConstraintDescriptor constraintGetForName(String name) {
        return null;
    }

    @Override
    public boolean indexExists(IndexDescriptor index) {
        return false;
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetAll() {
        return Collections.emptyIterator();
    }

    @Override
    public Collection<IndexDescriptor> valueIndexesGetRelated(
        int[] tokens, int propertyKeyId, EntityType entityType
    ) {
        return valueIndexesGetRelated(tokens, new int[]{propertyKeyId}, entityType);
    }

    @Override
    public Collection<IndexDescriptor> valueIndexesGetRelated(
        int[] tokens, int[] propertyKeyIds, EntityType entityType
    ) {
        return Collections.emptyList();
    }

    @Override
    public Collection<IndexBackedConstraintDescriptor> uniquenessConstraintsGetRelated(
        int[] labels,
        int propertyKeyId,
        EntityType entityType
    ) {
        return Collections.emptyList();
    }

    @Override
    public Collection<IndexBackedConstraintDescriptor> uniquenessConstraintsGetRelated(
        int[] tokens,
        int[] propertyKeyIds,
        EntityType entityType
    ) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasRelatedSchema(int[] labels, int propertyKey, EntityType entityType) {
        return false;
    }

    @Override
    public boolean hasRelatedSchema(int label, EntityType entityType) {
        return false;
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForSchema(SchemaDescriptor descriptor) {
        return Collections.emptyIterator();
    }

    @Override
    public boolean constraintExists(ConstraintDescriptor descriptor) {
        return false;
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForLabel(int labelId) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForRelationshipType(int typeId) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetAll() {
        return Collections.emptyIterator();
    }

    @Override
    public IntSet[] constraintsGetPropertyTokensForLogicalKey(int token, EntityType entityType) {
        return new IntSet[0];
    }

    @Override
    public Long indexGetOwningUniquenessConstraintId(IndexDescriptor index) {
        return null;
    }

    @Override
    public long countsForNode(int labelId, CursorContext cursorContext) {
        return counts.nodeCount(labelId, cursorContext);
    }

    @Override
    public long estimateCountsForNode(int labelId, CursorContext cursorContext) {
        return counts.estimateNodeCount(labelId, cursorContext);
    }

    @Override
    public long countsForRelationship(int startLabelId, int typeId, int endLabelId, CursorContext cursorContext) {
        return counts.relationshipCount(startLabelId, typeId, endLabelId, cursorContext);
    }

    @Override
    public long estimateCountsForRelationship(int startLabelId, int typeId, int endLabelId, CursorContext cursorContext) {
        return counts.estimateRelationshipCount(startLabelId, typeId, endLabelId, cursorContext);
    }

    @Override
    public long nodesGetCount(CursorContext cursorContext) {
        return graphStore.nodeCount();
    }

    @Override
    public int labelCount() {
        return graphStore.nodes().availableNodeLabels().size();
    }

    @Override
    public int propertyKeyCount() {
        int nodePropertyCount = graphStore
            .schema()
            .nodeSchema()
            .unionProperties()
            .size();
        int relPropertyCount = graphStore
            .schema()
            .relationshipSchema()
            .unionProperties()
            .size();

        return nodePropertyCount + relPropertyCount;
    }

    @Override
    public int relationshipTypeCount() {
        return graphStore.schema().relationshipSchema().entries().size();
    }

    @Override
    public <T> T getOrCreateSchemaDependantState(Class<T> type, Function<StorageReader, T> factory) {
        return type.cast(dependantState.computeIfAbsent(type, key -> factory.apply(this)));
    }

    @Override
    public AllNodeScan allNodeScan() {
        return new InMemoryNodeScan();
    }

    @Override
    public void close() {
        assert !closed;
        closed = true;
    }

    @Override
    public StorageSchemaReader schemaSnapshot() {
        return this;
    }

    @Override
    public TokenNameLookup tokenNameLookup() {
        return tokenHolders;
    }

    @Override
    public void visitAllCounts(CountsVisitor countsVisitor, CursorContext cursorContext) {
        for (NodeLabel label : graphStore.nodeLabels()) {
            var tokenHolder = tokenHolders.labelTokens();
            countsVisitor.visitNodeCount(tokenHolder.getIdByName(label.name()), graphStore.nodes().nodeCount(label));
        }

        for (RelationshipType relationshipType : graphStore.relationshipTypes()) {
            var tokenHolder = tokenHolders.relationshipTypeTokens();
            // we dont know start / end label for a reltype
            countsVisitor.visitRelationshipCount(
                -1,
                tokenHolder.getIdByName(relationshipType.name()),
                -1,
                graphStore.relationshipCount(relationshipType)
            );
        }
    }
}