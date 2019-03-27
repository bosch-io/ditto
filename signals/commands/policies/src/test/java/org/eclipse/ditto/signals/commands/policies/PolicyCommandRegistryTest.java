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
package org.eclipse.ditto.signals.commands.policies;


import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.junit.Test;


/**
 * Unit test for {@link PolicyCommandRegistryTest}.
 */
public class PolicyCommandRegistryTest {


    @Test
    public void parsePolicyModifyCommand() {
        final PolicyModifyCommandRegistry commandRegistry = PolicyModifyCommandRegistry.newInstance();

        final ModifyPolicy command = ModifyPolicy.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.POLICY, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }


    @Test
    public void parsePolicyQueryCommand() {
        final PolicyQueryCommandRegistry commandRegistry = PolicyQueryCommandRegistry.newInstance();

        final RetrievePolicy command = RetrievePolicy.of(
                TestConstants.Policy.POLICY_ID, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }

}
