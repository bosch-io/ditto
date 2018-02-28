/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

import java.util.Collections;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * TODO doc
 *
 * Sorry, not really a test yet - class was used in order to manually test mapping functionality.
 */
//@RunWith(Parameterized.class)
public class PayloadMapperMustacheTest {

    private static final String CONTENT_TYPE = "application/json";

    private static final String MAPPING_TEMPLATE = "ditto_mappingString = " +
            "Mustache.render(\"Topic was: {{{topic}}}\\n\" +\n" +
            "\"Header correlation-id was: {{headers.correlation-id}}\", ditto_protocolJson);";

    private static MessageMapper javaScriptRhinoMapper;

//    @Parameterized.Parameters
//    public static List<Object[]> data() {
//        return Arrays.asList(new Object[20][0]);
//    }

    @BeforeClass
    public static void setup() {
        javaScriptRhinoMapper = MessageMappers.createJavaScriptMessageMapper();
        MessageMapperConfiguration configuration = JavaScriptMessageMapperFactory
                .createJavaScriptMessageMapperConfigurationBuilder(Collections.emptyMap())
                .contentType(CONTENT_TYPE)
                .outgoingMappingScript(MAPPING_TEMPLATE)
                .loadMustacheJS(true).build();
        javaScriptRhinoMapper.configureWithValidation(configuration);
    }

    @Test
    public void testRhinoMapper() {
        final Thing newThing = Thing.newBuilder()
                .setId("org.eclipse.ditto:foo-bar")
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final CreateThing createThing =
                CreateThing.of(newThing, null, DittoHeaders.newBuilder().correlationId("cor-0815").build());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(createThing);

        final long startTs = System.nanoTime();
        final ExternalMessage rawMessage = javaScriptRhinoMapper.map(adaptable);
        System.out.println(rawMessage);
        System.out.println("Duration: " + (System.nanoTime() - startTs) / 1000000.0 + "ms");
    }
}
