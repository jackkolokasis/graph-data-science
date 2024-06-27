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
package org.neo4j.gds.applications.algorithms.machinery;

import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.GraphLoaderContext;
import org.neo4j.gds.api.User;
import org.neo4j.gds.core.utils.progress.TaskRegistryFactory;
import org.neo4j.gds.core.utils.warnings.UserLogRegistryFactory;
import org.neo4j.gds.core.utils.warnings.UserLogStore;
import org.neo4j.gds.termination.TerminationFlag;

/**
 * This is a handy class for transporting similarly scoped dependencies through layers.
 * And especially useful when that list grows or shrinks - fewer sites to edit innit.
 */
public final class RequestScopedDependencies<CONTEXT> {
    private final DatabaseId databaseId;
    private final GraphLoaderContext graphLoaderContext;
    private final CONTEXT domainContext;
    private final TaskRegistryFactory taskRegistryFactory;
    private final TerminationFlag terminationFlag;
    private final User user;
    private final UserLogRegistryFactory userLogRegistryFactory;
    private final UserLogStore userLogStore;

    /**
     * Over-doing it with a private constructor?
     * <p>
     * I just really like the <code>RequestScopedDependencies.builder().build()</code> form
     */
    private RequestScopedDependencies(
        DatabaseId databaseId,
        GraphLoaderContext graphLoaderContext,
        TaskRegistryFactory taskRegistryFactory,
        TerminationFlag terminationFlag,
        User user,
        CONTEXT domainContext,
        UserLogRegistryFactory userLogRegistryFactory,
        UserLogStore userLogStore
    ) {
        this.databaseId = databaseId;
        this.graphLoaderContext = graphLoaderContext;
        this.domainContext = domainContext;
        this.taskRegistryFactory = taskRegistryFactory;
        this.terminationFlag = terminationFlag;
        this.user = user;
        this.userLogRegistryFactory = userLogRegistryFactory;
        this.userLogStore = userLogStore;
    }
    public static <R> RequestScopedDependenciesBuilder<R> builder(){
        return  new RequestScopedDependenciesBuilder<R>();
    }

    public DatabaseId getDatabaseId() {
        return databaseId;
    }

    public GraphLoaderContext getGraphLoaderContext() {
        return graphLoaderContext;
    }


    public TaskRegistryFactory getTaskRegistryFactory() {
        return taskRegistryFactory;
    }

    public TerminationFlag getTerminationFlag() {
        return terminationFlag;
    }

    public User getUser() {
        return user;
    }

    public UserLogRegistryFactory getUserLogRegistryFactory() {
        return userLogRegistryFactory;
    }

    public UserLogStore getUserLogStore() {
        return userLogStore;
    }

    public CONTEXT getDomainContext() {
        return domainContext;
    }


    /**
     * A handy builder where you can include as many or as few components as you are interested in.
     * We deliberately do not have defaults,
     * because trying to reconcile convenience across all usages is an error-prone form of coupling.
     */
    public static class RequestScopedDependenciesBuilder<CONTEXT> {
        private DatabaseId databaseId;
        private GraphLoaderContext graphLoaderContext;
        private CONTEXT context;
        private TerminationFlag terminationFlag;
        private TaskRegistryFactory taskRegistryFactory;
        private User user;
        private UserLogRegistryFactory userLogRegistryFactory;
        private UserLogStore userLogStore;

        public RequestScopedDependenciesBuilder<CONTEXT> with(DatabaseId databaseId) {
            this.databaseId = databaseId;
            return this;
        }

        public RequestScopedDependenciesBuilder<CONTEXT> with(GraphLoaderContext graphLoaderContext) {
            this.graphLoaderContext = graphLoaderContext;
            return this;
        }



        public RequestScopedDependenciesBuilder<CONTEXT> with(TaskRegistryFactory taskRegistryFactory) {
            this.taskRegistryFactory = taskRegistryFactory;
            return this;
        }

        public RequestScopedDependenciesBuilder<CONTEXT> with(TerminationFlag terminationFlag) {
            this.terminationFlag = terminationFlag;
            return this;
        }

        public RequestScopedDependenciesBuilder<CONTEXT> with(User user) {
            this.user = user;
            return this;
        }

        public RequestScopedDependenciesBuilder<CONTEXT> with(UserLogRegistryFactory userLogRegistryFactory) {
            this.userLogRegistryFactory = userLogRegistryFactory;
            return this;
        }

        public RequestScopedDependenciesBuilder<CONTEXT> with(UserLogStore userLogStore) {
            this.userLogStore = userLogStore;
            return this;
        }

        public RequestScopedDependenciesBuilder<CONTEXT> with(CONTEXT context) {
            this.context = context;
            return this;
        }

        public RequestScopedDependencies<CONTEXT> build() {
            return new RequestScopedDependencies<>(
                databaseId,
                graphLoaderContext,
                taskRegistryFactory,
                terminationFlag,
                user,
                context,
                userLogRegistryFactory,
                userLogStore
            );
        }
    }
}
