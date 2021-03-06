/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import org.eclipse.ditto.base.model.signals.events.Event;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Behavior to modify this actor's {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
public interface ConnectivityConfigModifiedBehavior extends Actor {

    /**
     * Injectable behavior to handle {@code ConnectivityConfigBuildable}.
     *
     * @return behavior to handle {@code ConnectivityConfigBuildable}.
     */
    default AbstractActor.Receive connectivityConfigModifiedBehavior() {
        return ReceiveBuilder.create()
                .match(Event.class, event -> getConnectivityConfigProvider().canHandle(event), this::handleEvent)
                .build();
    }

    /**
     * Handles the received event by converting it to a {@link ConnectivityConfig} and passing it to
     * {@link #onConnectivityConfigModified(ConnectivityConfig)}.
     *
     * @param event the received event
     */
    default void handleEvent(final Event<?> event) {
        getConnectivityConfigProvider().handleEvent(event).ifPresent(this::onConnectivityConfigModified);
    }

    /**
     * @return a {@link ConnectionContextProvider} required to register this actor for config changes
     */
    default ConnectionContextProvider getConnectivityConfigProvider() {
        return ConnectionContextProviderFactory.getInstance(context().system());
    }

    /**
     * This method is called when a config modification is received. Implementations must handle the modified config
     * appropriately i.e. check if any relevant config has changed and re-initialize state if necessary.
     *
     * @param connectivityConfig the modified config
     */
    void onConnectivityConfigModified(ConnectivityConfig connectivityConfig);
}
