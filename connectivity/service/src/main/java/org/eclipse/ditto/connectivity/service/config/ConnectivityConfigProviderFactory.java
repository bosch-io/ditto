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

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

import com.typesafe.config.Config;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * Factory to instantiate new {@link ConnectionContextProvider}s.
 */
public final class ConnectivityConfigProviderFactory implements Extension {

    /**
     * If this config property is {@code false} then {@code #getInstance} will throw an exception if no config
     * provider other than the {@link #DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS} is found.
     */
    private static final String DEFAULT_CONFIG_PROVIDER_CONFIG = "ditto.connectivity.default-config-provider";

    /**
     * If no other implementation than #DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS is found, either this
     * implementation is used as a fallback or an exception is thrown, depending on the config value of
     * {@value #DEFAULT_CONFIG_PROVIDER_CONFIG}.
     */
    private static final Class<DittoConnectionContextProvider>
            DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS = DittoConnectionContextProvider.class;

    /**
     * Holds the instance of the {@link ConnectionContextProvider}.
     */
    private final ConnectionContextProvider connectionContextProvider;

    /**
     * Returns the {@link ConnectionContextProvider} instance.
     *
     * @return the instance of the {@link ConnectionContextProvider}
     */
    public ConnectionContextProvider getInstance() {
        return connectionContextProvider;
    }

    /**
     * Returns the {@link ConnectionContextProvider} instance.
     *
     * @param actorSystem the actor system
     * @return the instance of the {@link ConnectionContextProvider}
     */
    public static ConnectionContextProvider getInstance(final ActorSystem actorSystem) {
        return ConnectivityConfigProviderFactory.get(actorSystem).getInstance();
    }

    private ConnectivityConfigProviderFactory(final ActorSystem actorSystem) {
        final Config config = actorSystem.settings().config();
        final boolean loadDefaultProvider = config.getBoolean(DEFAULT_CONFIG_PROVIDER_CONFIG);

        final Class<? extends ConnectionContextProvider> providerClass =
                findProviderClass(c -> filterDefaultProvider(c, loadDefaultProvider));

        try {
            final ClassTag<ConnectionContextProvider> tag =
                    ClassTag$.MODULE$.apply(ConnectionContextProvider.class);
            final Tuple2<Class<?>, Object> args = new Tuple2<>(ActorSystem.class, actorSystem);
            final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
            final Try<ConnectionContextProvider> providerBox = dynamicAccess.createInstanceFor(providerClass,
                    CollectionConverters.asScala(Collections.singleton(args)).toList(), tag);
            connectionContextProvider = providerBox.get();
        } catch (final Exception e) {
            throw configProviderInstantiationFailed(providerClass, e);
        }
    }

    private static Class<? extends ConnectionContextProvider> findProviderClass(
            final Predicate<Class<? extends ConnectionContextProvider>> classPredicate) {

        final Iterable<Class<? extends ConnectionContextProvider>> subclasses =
                ClassIndex.getSubclasses(ConnectionContextProvider.class);

        final List<Class<? extends ConnectionContextProvider>> candidates =
                StreamSupport.stream(subclasses.spliterator(), false)
                        .filter(classPredicate)
                        .collect(Collectors.toList());

        if (candidates.size() == 1) {
            return candidates.get(0);
        } else {
            throw ConnectivityConfigProviderFactory.configProviderNotFound(candidates);
        }
    }

    private static boolean filterDefaultProvider(final Class<? extends ConnectionContextProvider> c,
            final boolean loadDefaultProvider) {

        if (loadDefaultProvider) {
            return true;
        } else {
            return !DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS.equals(c);
        }
    }

    private static DittoRuntimeException configProviderNotFound(
            final List<Class<? extends ConnectionContextProvider>> candidates) {
        return ConnectivityConfigProviderMissingException.newBuilder(candidates).build();
    }

    private static DittoRuntimeException configProviderInstantiationFailed(final Class<?
            extends ConnectionContextProvider> c, final Exception cause) {
        return ConnectivityConfigProviderFailedException.newBuilder(c)
                .cause(cause)
                .build();
    }

    /**
     * Load the {@code ConnectivityConfigProviderFactory} extension.
     *
     * @param actorSystem The actor system in which to load the provider.
     * @return the {@code ConnectivityConfigProviderFactory}.
     */
    public static ConnectivityConfigProviderFactory get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide a {@link ConnectivityConfigProviderFactory}.
     */
    private static final class ExtensionId extends AbstractExtensionId<ConnectivityConfigProviderFactory> {

        private static final ExtensionId INSTANCE =
                new ExtensionId();

        @Override
        public ConnectivityConfigProviderFactory createExtension(final ExtendedActorSystem system) {
            return new ConnectivityConfigProviderFactory(system);
        }

    }

}
