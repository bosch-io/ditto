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
package org.eclipse.ditto.gateway.service.streaming.actors;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.signals.events.ThingEventToThingConverter;

import akka.actor.ActorRef;

/**
 * Store of the needed information about a streaming session of a single streaming type.
 */
public final class StreamingSession {

    private final List<String> namespaces;
    private final Predicate<Thing> thingPredicate;
    @Nullable private final ThingFieldSelector extraFields;
    private final ActorRef streamingSessionActor;
    private final ThreadSafeDittoLoggingAdapter logger;

    private StreamingSession(final List<String> namespaces, @Nullable final Criteria eventFilterCriteria,
            @Nullable final ThingFieldSelector extraFields, final ActorRef streamingSessionActor,
            final ThreadSafeDittoLoggingAdapter logger) {
        this.namespaces = namespaces;
        thingPredicate = eventFilterCriteria == null
                ? thing -> true
                : ThingPredicateVisitor.apply(eventFilterCriteria);
        this.extraFields = extraFields;
        this.streamingSessionActor = streamingSessionActor;
        this.logger = logger;
    }

    static StreamingSession of(final List<String> namespaces, @Nullable final Criteria eventFilterCriteria,
            @Nullable final ThingFieldSelector extraFields, final ActorRef streamingSessionActor,
            final ThreadSafeDittoLoggingAdapter logger) {

        return new StreamingSession(namespaces, eventFilterCriteria, extraFields, streamingSessionActor, logger);
    }

    /**
     * @return namespaces of the session.
     */
    public List<String> getNamespaces() {
        return namespaces;
    }

    /**
     * @return extra fields of the session if any is given.
     */
    public Optional<ThingFieldSelector> getExtraFields() {
        return Optional.ofNullable(extraFields);
    }

    /**
     * Merge any thing information in a signal event together with extra fields from signal enrichment.
     * Thing events contain thing information. All other signals do not contain thing information.
     *
     * @param signal the signal.
     * @param extra extra fields from signal enrichment.
     * @return the merged thing if thing information exists in any of the 2 sources, or an empty thing otherwise.
     */
    public Thing mergeThingWithExtra(final Signal<?> signal, final JsonObject extra) {
        return ThingEventToThingConverter.mergeThingWithExtraFields(signal, extraFields, extra)
                .orElseGet(() -> Thing.newBuilder().build());
    }

    /**
     * Test whether a thing matches the filter defined in this session.
     *
     * @param thing the thing.
     * @return whether the thing passes the filter.
     */
    public boolean matchesFilter(final Thing thing) {
        return thingPredicate.test(thing);
    }

    public ActorRef getStreamingSessionActor() {
        return streamingSessionActor;
    }

    /**
     * @return the underlying logger of this session.
     */
    public ThreadSafeDittoLoggingAdapter getLogger() {
        return logger;
    }

}
