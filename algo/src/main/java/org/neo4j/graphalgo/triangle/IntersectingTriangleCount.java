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
package org.neo4j.graphalgo.triangle;

import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.IntersectionConsumer;
import org.neo4j.graphalgo.api.RelationshipIntersect;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeDoubleArray;
import org.neo4j.graphalgo.core.utils.paged.PagedAtomicIntegerArray;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * TriangleCount counts the number of triangles in the Graph as well
 * as the number of triangles that passes through a node.
 *
 * This impl uses another approach where all the triangles can be calculated
 * using set intersection methods of the graph itself.
 *
 * https://epubs.siam.org/doi/pdf/10.1137/1.9781611973198.1
 * http://www.cse.cuhk.edu.hk/~jcheng/papers/triangle_kdd11.pdf
 * https://i11www.iti.kit.edu/extra/publications/sw-fclt-05_t.pdf
 * http://www.math.cmu.edu/~ctsourak/tsourICDM08.pdf
 */
public class IntersectingTriangleCount extends Algorithm<IntersectingTriangleCount, IntersectingTriangleCount.TriangleCountResult> {

    private Graph graph;
    private ExecutorService executorService;
    private final int concurrency;
    private final AllocationTracker tracker;
    private final LongAdder triangleCount;
    private final AtomicLong queue;
    private PagedAtomicIntegerArray triangles;

    public IntersectingTriangleCount(
        Graph graph,
        ExecutorService executorService,
        int concurrency,
        AllocationTracker tracker,
        ProgressLogger progressLogger
    ) {
        this.graph = graph;
        this.tracker = tracker;
        this.executorService = executorService;
        this.concurrency = concurrency;
        triangles = PagedAtomicIntegerArray.newArray(graph.nodeCount(), tracker);
        triangleCount = new LongAdder();
        queue = new AtomicLong();
        this.progressLogger = progressLogger;
    }

    public IntersectingTriangleCount(
        Graph graph,
        ExecutorService executorService,
        int concurrency,
        AllocationTracker tracker
    ) {
        this(graph, executorService, concurrency, tracker, ProgressLogger.NULL_LOGGER);
    }

    @Override
    public final IntersectingTriangleCount me() {
        return this;
    }

    @Override
    public void release() {
        executorService = null;
        graph = null;
        triangles = null;
    }

    @Override
    public TriangleCountResult compute() {
        queue.set(0);
        triangleCount.reset();
        // create tasks
        final Collection<? extends Runnable> tasks = ParallelUtil.tasks(concurrency, () -> new IntersectTask(graph));
        // run
        ParallelUtil.run(tasks, executorService);

        // collect local clustering coefficients
        HugeDoubleArray localCCs = HugeDoubleArray.newArray(graph.nodeCount(), tracker);
        double localCCSum = 0.0;
        for (long i = 0; i < graph.nodeCount(); ++i) {
            double localCC = calculateCoefficient(triangles.get(i), graph.degree(i));
            localCCs.set(i, localCC);
            localCCSum += localCC;
        }
        // compute average clustering coefficient
        double averageClusteringCoefficient = localCCSum / graph.nodeCount();
        long globalTriangles = triangleCount.longValue();

        return TriangleCountResult.of(
            triangles,
            localCCs,
            globalTriangles,
            averageClusteringCoefficient
        );
    }

    private class IntersectTask implements Runnable, IntersectionConsumer {

        private RelationshipIntersect intersect;

        IntersectTask(Graph graph) {
            intersect = graph.intersection();
        }

        @Override
        public void run() {
            long node;
            while ((node = queue.getAndIncrement()) < graph.nodeCount() && running()) {
                intersect.intersectAll(node, this);
                // FIXME: This needs to be fixed when all the Triangle Count `alpha` procs are removed
                // NB: Logging without parameters is fine!
                // getProgressLogger().logProgress();
            }
        }

        @Override
        public void accept(final long nodeA, final long nodeB, final long nodeC) {
            // only use this triangle where the id's are in order, not the other 5
            if (nodeA < nodeB) { //  && nodeB < nodeC
                triangles.add((int) nodeA, 1);
                triangles.add((int) nodeB, 1);
                triangles.add((int) nodeC, 1);
                triangleCount.increment();
            }
        }
    }

    private double calculateCoefficient(int triangles, int degree) {
        if (triangles == 0) {
            return 0.0;
        }
        // local clustering coefficient C(v) = 2 * triangles(v) / (degree(v) * (degree(v) - 1))
        return ((double) (triangles << 1)) / (degree * (degree - 1));
    }

    @ValueClass
    interface TriangleCountResult {
        // value at index `i` is number of triangles for node with id `i`
        PagedAtomicIntegerArray localTriangles();

        // value at index `i` is local clustering coefficient for node with id `i`
        HugeDoubleArray localClusteringCoefficients();

        long globalTriangles();

        double averageClusteringCoefficient();

        static TriangleCountResult of(
            PagedAtomicIntegerArray triangles,
            HugeDoubleArray localClusteringCoefficients,
            long globalTriangles,
            double averageClusteringCoefficient
        ) {
            return ImmutableTriangleCountResult
                .builder()
                .localTriangles(triangles)
                .localClusteringCoefficients(localClusteringCoefficients)
                .globalTriangles(globalTriangles)
                .averageClusteringCoefficient(averageClusteringCoefficient)
                .build();
        }
    }
}
