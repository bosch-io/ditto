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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario4MultipleSubjects20 implements Scenario4MultipleSubjects {

    private final ScenarioSetup setup;

    public Scenario4MultipleSubjects20() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject_5 has READ granted on '/attributes/read_write'. "
                        + "Subject_6 has READ granted on '/attributes/read_write'. "
                        + "Subject_3 has WRITE granted on '/attributes/read_write'. "
                        + "Subject_6 has WRITE granted on '/attributes/read_write'. "
                        + "Is able to READ '/attributes/read_write' with hasPermissionsOnResourceOrAnySubresource()"
                        + "Is able to WRITE '/attributes/read_write' with hasPermissionsOnResourceOrAnySubresource()",
                //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_3, SUBJECT_5), //
                "/attributes/read_write", //
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

    @Override
    public Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction() {
        // algorithm invoked with hasPermissionsOnResourceOrAnySubresource! as we would like to know if the subject can read anywhere
        // in the hierarchy below the passed path:
        return algorithm -> algorithm.hasPermissionsOnResourceOrAnySubresource(getSetup());
    }
}
