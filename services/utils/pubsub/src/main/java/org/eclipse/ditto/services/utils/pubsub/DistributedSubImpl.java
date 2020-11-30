/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.pubsub;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.api.RemoveSubscriber;
import org.eclipse.ditto.services.utils.pubsub.api.Request;
import org.eclipse.ditto.services.utils.pubsub.api.SubAck;
import org.eclipse.ditto.services.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.services.utils.pubsub.api.Unsubscribe;

import akka.actor.ActorRef;
import akka.cluster.ddata.Replicator;
import akka.pattern.Patterns;

/**
 * Package-private implementation of {@link DistributedSub}.
 */
final class DistributedSubImpl implements DistributedSub {

    private static final long DDATA_CLUSTER_DELAY_FACTOR = 2;

    // package-private for unit tests
    final ActorRef subSupervisor;

    private final DistributedDataConfig config;
    private final Replicator.WriteConsistency writeConsistency;
    private final long ddataDelayInMillis;

    DistributedSubImpl(final DistributedDataConfig config, final ActorRef subSupervisor) {
        this.config = config;
        this.subSupervisor = subSupervisor;
        this.writeConsistency = (Replicator.WriteConsistency) Replicator.writeLocal();
        // make an optimistic delay estimation that should hold in the absence of excessive load
        final Duration clusterReplicationDelayEstimate = config.getAkkaReplicatorConfig().getNotifySubscribersInterval()
                .plus(config.getAkkaReplicatorConfig().getGossipInterval())
                .multipliedBy(DDATA_CLUSTER_DELAY_FACTOR);
        ddataDelayInMillis = clusterReplicationDelayEstimate.toMillis();
    }

    @Override
    public CompletionStage<SubAck> subscribeWithFilterAndGroup(final Collection<String> topics,
            final ActorRef subscriber,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group) {
        if (group != null) {
            checkNotEmpty(group, "group");
        }
        final Subscribe subscribe =
                Subscribe.of(new HashSet<>(topics), subscriber, writeConsistency, true, filter, group);
        return askSubSupervisor(subscribe);
    }

    @Override
    public CompletionStage<SubAck> unsubscribeWithAck(final Collection<String> topics,
            final ActorRef subscriber) {
        return askSubSupervisor(Unsubscribe.of(new HashSet<>(topics), subscriber, writeConsistency, true));
    }

    private CompletionStage<SubAck> askSubSupervisor(final Request request) {
        return Patterns.ask(subSupervisor, request, config.getWriteTimeout())
                .thenCompose(DistributedSubImpl::processAskResponse)
                .thenCompose(result -> {
                    final CompletableFuture<SubAck> resultFuture = new CompletableFuture<>();
                    resultFuture.completeOnTimeout(result, ddataDelayInMillis, TimeUnit.MILLISECONDS);
                    return resultFuture;
                });
    }

    @Override
    public void subscribeWithoutAck(final Collection<String> topics, final ActorRef subscriber) {
        final Request request =
                Subscribe.of(new HashSet<>(topics), subscriber,
                        (Replicator.WriteConsistency) Replicator.writeLocal(), false, null);
        subSupervisor.tell(request, subscriber);
    }

    @Override
    public void unsubscribeWithoutAck(final Collection<String> topics, final ActorRef subscriber) {
        final Request request =
                Unsubscribe.of(new HashSet<>(topics), subscriber,
                        (Replicator.WriteConsistency) Replicator.writeLocal(), false);
        subSupervisor.tell(request, subscriber);
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        final Request request =
                RemoveSubscriber.of(subscriber, (Replicator.WriteConsistency) Replicator.writeLocal(),
                        false);
        subSupervisor.tell(request, subscriber);
    }

    private static CompletionStage<SubAck> processAskResponse(final Object askResponse) {
        if (askResponse instanceof SubAck) {
            return CompletableFuture.completedStage((SubAck) askResponse);
        } else if (askResponse instanceof Throwable) {
            return CompletableFuture.failedStage((Throwable) askResponse);
        } else {
            return CompletableFuture.failedStage(new ClassCastException("Expect SubAck, got: " + askResponse));
        }
    }
}
