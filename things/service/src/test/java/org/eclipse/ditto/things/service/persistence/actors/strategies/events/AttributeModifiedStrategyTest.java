/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.Test;

/**
 * Unit test for {@link AttributeModifiedStrategy}.
 */
public final class AttributeModifiedStrategyTest extends AbstractStrategyTest {

    @Test
    public void appliesEventCorrectly() {
        final AttributeModifiedStrategy strategy = new AttributeModifiedStrategy();
        final AttributeModified event = AttributeModified.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, REVISION,
                TIMESTAMP, DittoHeaders.empty(), null);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setAttribute(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void appliesEventWithMetadataCorrectly() {
        final AttributeModifiedStrategy strategy = new AttributeModifiedStrategy();
        final AttributeModified event = AttributeModified.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, REVISION,
                TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Metadata expectedMetadata = Metadata.newBuilder()
                .set(Thing.JsonFields.ATTRIBUTES, JsonObject.newBuilder()
                        .set(ATTRIBUTE_POINTER.toString(), METADATA)
                        .build())
                .build();

        final Thing expected = THING.toBuilder()
                .setAttribute(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(expectedMetadata)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
