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
package org.eclipse.ditto.protocol.adapter.policies;

import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Base class for {@link org.eclipse.ditto.protocol.adapter.Adapter}s that handle policy commands.
 *
 * @param <T> the type of the policy command
 */
abstract class AbstractPolicyAdapter<T extends Signal<?>> extends AbstractAdapter<T> implements PolicyAdapter<T> {

    private final SignalMapper<T> signalMapper;

    /**
     * @param mappingStrategies the {@link MappingStrategies} used to convert {@link Adaptable}s to
     * {@link org.eclipse.ditto.base.model.signals.Signal}s
     * @param signalMapper the {@link SignalMapper} used to convert from a
     * {@link org.eclipse.ditto.base.model.signals.Signal} to an {@link Adaptable}
     * @param headerTranslator the header translator
     */
    protected AbstractPolicyAdapter(final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper, final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator, PolicyPathMatcher.getInstance());
        this.signalMapper = signalMapper;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }

}
