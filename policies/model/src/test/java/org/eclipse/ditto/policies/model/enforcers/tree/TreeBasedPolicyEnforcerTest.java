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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.junit.Test;

/**
 * Unit test for {@link TreeBasedPolicyEnforcer}.
 *
 * <em>The actual unit tests are located in TreeBasedPolicyAlgorithmTest of module policies-service.</em>
 */
public final class TreeBasedPolicyEnforcerTest {

    @Test
    public void tryToCreateInstanceWithNullPolicy() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TreeBasedPolicyEnforcer.createInstance(null))
                .withMessage("The %s must not be null!", "policy")
                .withNoCause();
    }

    @Test
    public void buildJsonViewForNullValue() {
        final PolicyId policyId = PolicyId.of("namespace", "id");
        final TreeBasedPolicyEnforcer underTest =
                TreeBasedPolicyEnforcer.createInstance(Policy.newBuilder(policyId).build());

        final JsonObject createdJsonView = underTest.buildJsonView(
                ResourceKey.newInstance("foo", "bar"),
                JsonFactory.nullObject(),
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("itsMe")),
                Permissions.none());

        final JsonObject expectedJsonView = JsonFactory.nullObject();

        assertThat(createdJsonView).isEqualTo(expectedJsonView);
    }

}
