/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.stream.testkit.javadsl.TestSource;
import org.apache.pekko.testkit.TestActor;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.filter.AcceptAllTracingFilter;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultStreamConfig;
import org.eclipse.ditto.thingsearch.service.common.config.StreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit tests for {@link EnforcementFlow}. Contains fix method order to allow for longer setup during first test.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class EnforcementFlowTest {

    private ActorSystem system;

    private TestPublisher.Probe<Collection<Metadata>> sourceProbe;
    private TestSubscriber.Probe<List<AbstractWriteModel>> sinkProbe;

    @BeforeClass
    public static void beforeClass() {
        final TracingConfig tracingConfigMock = Mockito.mock(TracingConfig.class);
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(true);
        Mockito.when(tracingConfigMock.getPropagationChannel()).thenReturn("default");
        Mockito.when(tracingConfigMock.getTracingFilter()).thenReturn(AcceptAllTracingFilter.getInstance());
        DittoTracing.init(tracingConfigMock);
    }

    @AfterClass
    public static void afterClass() {
        DittoTracing.reset();
    }
    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("actors-test.conf"));
    }

    @After
    public void cleanup() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void updateThingAndPolicyRevisions() {
        new TestKit(system) {{
            // GIVEN: enqueued metadata is out of date
            final long thingRev1 = 1L;
            final long thingRev2 = 99L;
            final long policyRev1 = 2L;
            final long policyRev2 = 98L;
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Metadata metadata =
                    Metadata.of(thingId, thingRev1, PolicyTag.of(policyId, policyRev1), null, Set.of(), null);
            final Collection<Metadata> input = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(input);
            sourceProbe.sendComplete();

            // WHEN: thing and policy are retrieved with up-to-date revisions
            thingsProbe.expectMsgClass(FiniteDuration.apply(10, TimeUnit.SECONDS), SudoRetrieveThing.class);
            final var thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).setRevision(thingRev2).build();
            final var thingJson = thing.toJson(FieldType.regularOrSpecial());
            assertThat(thingJson.getValue(Thing.JsonFields.REVISION)).contains(thingRev2);
            thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

            policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
            final var policy = Policy.newBuilder(policyId).setRevision(policyRev2).build();
            policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty()));

            // THEN: the write model contains up-to-date revisions
            final AbstractWriteModel writeModel = sinkProbe.expectNext().get(0);
            sinkProbe.expectComplete();
            assertThat(writeModel).isInstanceOf(ThingWriteModel.class);
            final var document = JsonObject.of(((ThingWriteModel) writeModel).getThingDocument().toJson());
            assertThat(document.getValue("_id")).contains(JsonValue.of(thingId));
            assertThat(document.getValue("policyId")).contains(JsonValue.of(policyId));
            assertThat(document.getValue("_revision")).contains(JsonValue.of(thingRev2));
            assertThat(document.getValue("__policyRev")).contains(JsonValue.of(policyRev2));
        }};
    }

    @Test
    public void ignoreCacheWhenRequestedToUpdate() {
        // GIVEN: the enqueued metadata is out-of-date
        final long thingRev1 = 1L;
        final long thingRev2 = 99L;
        final long policyRev1 = 2L;
        final long policyRev2 = 98L;
        final ThingId thingId = ThingId.of("thing:id");
        final PolicyId policyId = PolicyId.of("policy:id");
        final Metadata metadata1 = Metadata.of(thingId, thingRev1, PolicyTag.of(policyId, policyRev1), null, Set.of(), null);

        final TestProbe thingsProbe = TestProbe.apply(system);
        final TestProbe policiesProbe = TestProbe.apply(system);

        final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
        final EnforcementFlow underTest =
                EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                        system.getScheduler());

        materializeTestProbes(underTest);

        sinkProbe.ensureSubscription();
        sourceProbe.ensureSubscription();
        sinkProbe.request(2);
        sourceProbe.sendNext(List.of(metadata1));

        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        final var thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).setRevision(thingRev2).build();
        final var thingJson = thing.toJson(FieldType.regularOrSpecial());
        thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

        // GIVEN: enforcer cache is loaded with out-of-date policy
        policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        final var policy1 = Policy.newBuilder(policyId).setRevision(policyRev1).build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy1, DittoHeaders.empty()));

        final AbstractWriteModel writeModel1 = sinkProbe.expectNext().get(0);
        assertThat(writeModel1).isInstanceOf(ThingWriteModel.class);
        final var document1 = JsonObject.of(((ThingWriteModel) writeModel1).getThingDocument().toJson());
        assertThat(document1.getValue("_revision")).contains(JsonValue.of(thingRev2));
        assertThat(document1.getValue("__policyRev")).contains(JsonValue.of(policyRev1));

        // WHEN: a metadata with 'invalidateCache' flag is enqueued
        final Metadata metadata2 = metadata1.invalidateCaches(true, true);
        sourceProbe.sendNext(List.of(metadata2));
        sourceProbe.sendComplete();
        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

        // THEN: policy cache is reloaded
        policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        final var policy2 = Policy.newBuilder(policyId).setRevision(policyRev2).build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy2, DittoHeaders.empty()));

        // THEN: write model contains up-to-date policy revisions.
        final AbstractWriteModel writeModel2 = sinkProbe.expectNext().get(0);
        sinkProbe.expectComplete();
        assertThat(writeModel2).isInstanceOf(ThingWriteModel.class);
        final var document2 = JsonObject.of(((ThingWriteModel) writeModel2).getThingDocument().toJson());
        assertThat(document2.getValue("_revision")).contains(JsonValue.of(thingRev2));
        assertThat(document2.getValue("__policyRev")).contains(JsonValue.of(policyRev2));
    }

    @Test
    public void importedPolicyIsOnlyLoadedOnceWhenTwoDifferentPoliciesImportFromItAndInvalidatesImportedOnlyOnce() {
        final long thing1Rev1 = 1L;
        final long thing1Rev2 = 1L;
        final long thing2Rev1 = 2L;
        final long thing2Rev2 = 2L;
        final long importedPolicyRev1 = 3L;
        final long importedPolicyRev2 = 4L;
        final long policy1Rev1 = 2L;
        final long policy2Rev1 = 3L;
        final ThingId thingId1 = ThingId.of("thing:id-1");
        final ThingId thingId2 = ThingId.of("thing:id-2");
        final PolicyId motherPolicyId = PolicyId.of("policy:mother"); // mother should never be loaded
        final PolicyId importedPolicyId = PolicyId.of("policy:imported");
        final PolicyId importingPolicy1Id = PolicyId.of("policy:importing-1");
        final PolicyId importingPolicy2Id = PolicyId.of("policy:importing-2");
        final Metadata metadata1 = Metadata.of(thingId1, thing1Rev1, PolicyTag.of(importingPolicy1Id, policy1Rev1),
                PolicyTag.of(importedPolicyId, importedPolicyRev2), Set.of(), null);
        final Metadata metadata2 = Metadata.of(thingId2, thing2Rev1, PolicyTag.of(importingPolicy2Id, policy2Rev1),
                PolicyTag.of(importedPolicyId, importedPolicyRev2), Set.of(), null);

        final TestProbe thingsProbe = TestProbe.apply(system);
        final TestProbe policiesProbe = TestProbe.apply(system);

        final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
        final EnforcementFlow underTest =
                EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                        system.getScheduler());

        materializeTestProbes(underTest);

        sinkProbe.ensureSubscription();
        sourceProbe.ensureSubscription();
        sinkProbe.request(2);
        sourceProbe.sendNext(List.of(metadata1));

        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        final var thing = Thing.newBuilder().setId(thingId1).setPolicyId(importingPolicy1Id).setRevision(thing1Rev2).build();
        final var thingJson = thing.toJson(FieldType.regularOrSpecial());
        thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

        final SudoRetrievePolicy sudoRetrievePolicy1 = policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat(sudoRetrievePolicy1.getEntityId().toString()).isEqualTo(importingPolicy1Id.toString());
        final var policy1 = Policy.newBuilder(importingPolicy1Id)
                .setPolicyImport(PolicyImport.newInstance(importedPolicyId, null))
                .setRevision(policy1Rev1)
                .build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(importingPolicy1Id, policy1, DittoHeaders.empty()));

        final SudoRetrievePolicy sudoRetrieveImportedPolicy = policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat(sudoRetrieveImportedPolicy.getEntityId().toString()).isEqualTo(importedPolicyId.toString());
        final var importedPolicy = Policy.newBuilder(importedPolicyId)
                .setPolicyImport(PolicyImport.newInstance(motherPolicyId, null))
                .setRevision(importedPolicyRev1).build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(importedPolicyId, importedPolicy, DittoHeaders.empty()));

        policiesProbe.expectNoMessage();

        final AbstractWriteModel writeModel1 = sinkProbe.expectNext().get(0);
        assertThat(writeModel1).isInstanceOf(ThingWriteModel.class);
        final var document1 = JsonObject.of(((ThingWriteModel) writeModel1).getThingDocument().toJson());
        assertThat(document1.getValue(PersistenceConstants.FIELD_REVISION)).contains(JsonValue.of(thing1Rev2));
        assertThat(document1.getValue(PersistenceConstants.FIELD_POLICY_REVISION)).contains(JsonValue.of(policy1Rev1));
        assertThat(document1.getValue(PersistenceConstants.FIELD_REFERENCED_POLICIES))
                .contains(JsonArray.of(
                        PolicyTag.of(importedPolicyId, importedPolicyRev1).toJson(),
                        PolicyTag.of(importingPolicy1Id, policy1Rev1).toJson()
                ));

        sinkProbe.ensureSubscription();
        sourceProbe.ensureSubscription();
        sinkProbe.request(2);
        sourceProbe.sendNext(List.of(metadata2));

        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        final var thing2 = Thing.newBuilder().setId(thingId2).setPolicyId(importingPolicy2Id).setRevision(thing2Rev2).build();
        final var thing2Json = thing2.toJson(FieldType.regularOrSpecial());
        thingsProbe.reply(SudoRetrieveThingResponse.of(thing2Json, DittoHeaders.empty()));

        // GIVEN: enforcer cache is loaded with out-of-date policy
        final SudoRetrievePolicy sudoRetrievePolicy2 = policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat(sudoRetrievePolicy2.getEntityId().toString()).isEqualTo(importingPolicy2Id.toString());
        final var policy2 = Policy.newBuilder(importingPolicy2Id)
                .setPolicyImport(PolicyImport.newInstance(importedPolicyId, null))
                .setRevision(policy2Rev1)
                .build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(importingPolicy2Id, policy2, DittoHeaders.empty()));

        policiesProbe.expectNoMessage();

        final AbstractWriteModel writeModel2 = sinkProbe.expectNext().get(0);
        assertThat(writeModel2).isInstanceOf(ThingWriteModel.class);
        final var document2 = JsonObject.of(((ThingWriteModel) writeModel2).getThingDocument().toJson());
        assertThat(document2.getValue(PersistenceConstants.FIELD_REVISION)).contains(JsonValue.of(thing2Rev2));
        assertThat(document2.getValue(PersistenceConstants.FIELD_POLICY_REVISION)).contains(JsonValue.of(policy2Rev1));
        assertThat(document2.getValue(PersistenceConstants.FIELD_REFERENCED_POLICIES))
                .contains(JsonArray.of(
                        PolicyTag.of(importedPolicyId, importedPolicyRev1).toJson(),
                        PolicyTag.of(importingPolicy2Id, policy2Rev1).toJson()
                ));



        // WHEN: a metadata with 'invalidateCache' flag is enqueued
        final Metadata metadata1_2 = metadata1
                .withCausingPolicyTag(PolicyTag.of(importedPolicyId, importedPolicyRev2))
                .invalidateCaches(false, true);
        sinkProbe.request(2);
        sourceProbe.sendNext(List.of(metadata1_2));

        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

        // THEN: policy cache is reloaded
        final SudoRetrievePolicy sudoRetrievePolicy1_2 = policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat(sudoRetrievePolicy1_2.getEntityId().toString()).isEqualTo(importingPolicy1Id.toString());
        final var policy1_2 = Policy.newBuilder(importingPolicy1Id)
                .setPolicyImport(PolicyImport.newInstance(importedPolicyId, null))
                .setRevision(policy1Rev1)
                .build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(importingPolicy1Id, policy1_2, DittoHeaders.empty()));

        final SudoRetrievePolicy sudoRetrieveImportedPolicy_2 = policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat(sudoRetrieveImportedPolicy_2.getEntityId().toString()).isEqualTo(importedPolicyId.toString());
        final var importedPolicy_2 = Policy.newBuilder(importedPolicyId)
                .setPolicyImport(PolicyImport.newInstance(motherPolicyId, null))
                .setRevision(importedPolicyRev2).build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(importedPolicyId, importedPolicy_2, DittoHeaders.empty()));

        // THEN: write model contains up-to-date policy revisions.
        final AbstractWriteModel writeModel1_2 = sinkProbe.expectNext().get(0);
        assertThat(writeModel1_2).isInstanceOf(ThingWriteModel.class);
        final var document1_2 = JsonObject.of(((ThingWriteModel) writeModel1_2).getThingDocument().toJson());
        assertThat(document1_2.getValue(PersistenceConstants.FIELD_REVISION)).contains(JsonValue.of(thing1Rev2));
        assertThat(document1_2.getValue(PersistenceConstants.FIELD_POLICY_REVISION)).contains(JsonValue.of(policy1Rev1));
        assertThat(document1_2.getValue(PersistenceConstants.FIELD_REFERENCED_POLICIES))
                .contains(JsonArray.of(
                        PolicyTag.of(importedPolicyId, importedPolicyRev2).toJson(),
                        PolicyTag.of(importingPolicy1Id, policy1Rev1).toJson()
                ));

        final Metadata metadata2_2 = metadata2
                .withCausingPolicyTag(PolicyTag.of(importedPolicyId, importedPolicyRev2))
                .invalidateCaches(false, true);
        sinkProbe.request(2);
        sourceProbe.sendNext(List.of(metadata2_2));
        sourceProbe.sendComplete();

        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        thingsProbe.reply(SudoRetrieveThingResponse.of(thing2Json, DittoHeaders.empty()));

        // THEN: policy cache is reloaded
        final SudoRetrievePolicy sudoRetrievePolicy2_2 = policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat(sudoRetrievePolicy2_2.getEntityId().toString()).isEqualTo(importingPolicy2Id.toString());
        final var policy2_2 = Policy.newBuilder(importingPolicy2Id)
                .setPolicyImport(PolicyImport.newInstance(importedPolicyId, null))
                .setRevision(policy2Rev1)
                .build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(importingPolicy2Id, policy2_2, DittoHeaders.empty()));

        policiesProbe.expectNoMessage();

        // THEN: write model contains up-to-date policy revisions.
        final AbstractWriteModel writeModel2_2 = sinkProbe.expectNext().get(0);
        sinkProbe.expectComplete();
        assertThat(writeModel2_2).isInstanceOf(ThingWriteModel.class);
        final var document2_2 = JsonObject.of(((ThingWriteModel) writeModel2_2).getThingDocument().toJson());
        assertThat(document2_2.getValue(PersistenceConstants.FIELD_REVISION)).contains(JsonValue.of(thing2Rev2));
        assertThat(document2_2.getValue(PersistenceConstants.FIELD_POLICY_REVISION)).contains(JsonValue.of(policy2Rev1));
        assertThat(document2_2.getValue(PersistenceConstants.FIELD_REFERENCED_POLICIES))
                .contains(JsonArray.of(
                        PolicyTag.of(importedPolicyId, importedPolicyRev2).toJson(),
                        PolicyTag.of(importingPolicy2Id, policy2Rev1).toJson()
                ));
    }

    @Test
    public void computeThingCacheValueFromThingEvents() {
        new TestKit(system) {{
            // GIVEN: enqueued metadata contains events sufficient to determine the thing state
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final List<ThingEvent<?>> events = List.of(
                    ThingModified.of(thing, 1, null, headers, null),
                    ThingDeleted.of(thingId, 2, null, headers, null),
                    ThingCreated.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            null, headers, null),
                    ThingMerged.of(thingId, JsonPointer.of("attributes/y"), JsonValue.of(6), 4, null, headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, null, headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, null, headers, null)
            );

            final Metadata metadata =
                    Metadata.of(thingId, 5L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            thingsProbe.expectMsgClass(Duration.apply(30, TimeUnit.SECONDS), SudoRetrieveThing.class);
            thingsProbe.reply(SudoRetrieveThingResponse.of(JsonObject.empty(), DittoHeaders.empty()));
            // WHEN: policy is retrieved with up-to-date revisions
            policiesProbe.expectMsgClass(Duration.apply(30, TimeUnit.SECONDS), SudoRetrievePolicy.class);
            final var policy = Policy.newBuilder(policyId).setRevision(1).build();
            policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty()));

            // THEN: the write model contains up-to-date revisions
            final AbstractWriteModel writeModel = sinkProbe.expectNext().get(0);
            sinkProbe.expectComplete();
            assertThat(writeModel).isInstanceOf(ThingWriteModel.class);
            final var document = JsonObject.of(((ThingWriteModel) writeModel).getThingDocument().toJson());
            assertThat(document.getValue("_id")).contains(JsonValue.of(thingId));
            assertThat(document.getValue("policyId")).contains(JsonValue.of(policyId));
            assertThat(document.getValue("_revision")).contains(JsonValue.of(6));
            assertThat(document.getValue("__policyRev")).contains(JsonValue.of(1));
            assertThat(document.getValue("t/attributes")).contains(JsonObject.of("{\"x\":5,\"y\":6,\"z\":7}"));

            // THEN: thing is computed in the cache
            thingsProbe.expectNoMessage(FiniteDuration.Zero());
        }};
    }

    @Test
    public void computeThingCacheValueFromThingEventsWhenLastEventWasDeleted() {
        new TestKit(system) {{
            // GIVEN: enqueued metadata contains events sufficient to determine the thing state
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final Instant deletedTime = Instant.now();
            final List<ThingEvent<?>> events = List.of(
                    ThingModified.of(thing, 1, deletedTime.minusSeconds(1), headers, null),
                    ThingDeleted.of(thingId, 2, deletedTime, headers, null),
                    ThingCreated.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            deletedTime.minusSeconds(5), headers, null),
                    ThingMerged.of(thingId, JsonPointer.of("attributes/y"), JsonValue.of(6), 4,
                            deletedTime.minusSeconds(4), headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, deletedTime.minusSeconds(3),
                            headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, deletedTime.minusSeconds(2), headers, null)
            );

            final Metadata metadata =
                    Metadata.of(thingId, 5L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            // WHEN
            thingsProbe.expectMsgClass(Duration.apply(30, TimeUnit.SECONDS), SudoRetrieveThing.class);
            thingsProbe.reply(SudoRetrieveThingResponse.of(JsonObject.empty(), DittoHeaders.empty()));

            // THEN: the write model contains up-to-date revisions
            final AbstractWriteModel deleteModel = sinkProbe.expectNext().get(0);
            sinkProbe.expectComplete();
            assertThat(deleteModel).isInstanceOf(ThingDeleteModel.class);
            final var modelMetadata = deleteModel.getMetadata();
            assertThat(modelMetadata.getThingId().toString()).hasToString(thingId.toString());

            // THEN: thing is computed in the cache
            thingsProbe.expectNoMessage(FiniteDuration.Zero());
        }};
    }

    @Test
    public void forceRetrieveThing() {
        new TestKit(system) {{
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final List<ThingEvent<?>> events = List.of(
                    ThingModified.of(thing, 1, null, headers, null),
                    ThingDeleted.of(thingId, 2, null, headers, null),
                    ThingCreated.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            null, headers, null),
                    ThingMerged.of(thingId, JsonPointer.of("attributes/y"), JsonValue.of(6), 4, null, headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, null, headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, null, headers, null)
            );

            final Metadata metadata = Metadata.of(thingId, 6L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null)
                    .invalidateCaches(true, true);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            assertThat((CharSequence) thingsProbe.expectMsgClass(SudoRetrieveThing.class).getEntityId())
                    .isEqualTo(thingId);
        }};
    }

    @Test
    public void eventSequenceNumberTooLow() {
        new TestKit(system) {{
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final List<ThingEvent<?>> events = List.of(
                    ThingModified.of(thing, 1, null, headers, null),
                    ThingDeleted.of(thingId, 2, null, headers, null),
                    ThingCreated.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            null, headers, null),
                    ThingMerged.of(thingId, JsonPointer.of("attributes/y"), JsonValue.of(6), 4, null, headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, null, headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, null, headers, null)
            );

            final Metadata metadata =
                    Metadata.of(thingId, 7L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            assertThat((CharSequence) thingsProbe.expectMsgClass(SudoRetrieveThing.class).getEntityId())
                    .isEqualTo(thingId);
        }};
    }

    @Test
    public void eventMissed() {
        new TestKit(system) {{
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final List<ThingEvent<?>> events = List.of(
                    ThingModified.of(thing, 1, null, headers, null),
                    ThingDeleted.of(thingId, 2, null, headers, null),
                    ThingCreated.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            null, headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, null, headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, null, headers, null)
            );

            final Metadata metadata =
                    Metadata.of(thingId, 6L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            assertThat((CharSequence) thingsProbe.expectMsgClass(SudoRetrieveThing.class).getEntityId())
                    .isEqualTo(thingId);
        }};
    }

    @Test
    public void noInitialCreatedOrDeletedEvent() {
        new TestKit(system) {{
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final List<ThingEvent<?>> events = List.of(
                    ThingModified.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            null, headers, null),
                    ThingMerged.of(thingId, JsonPointer.of("attributes/y"), JsonValue.of(6), 4, null, headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, null, headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, null, headers, null)
            );

            final Metadata metadata =
                    Metadata.of(thingId, 6L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            // WHEN: thing and policy caches are loaded
            final var sudoRetrieveThing = thingsProbe.expectMsgClass(SudoRetrieveThing.class);
            thingsProbe.reply(SudoRetrieveThingResponse.of(
                    thing.toBuilder().setRevision(2).build().toJson(FieldType.all()),
                    sudoRetrieveThing.getDittoHeaders()));
            policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
            final var policy = Policy.newBuilder(policyId).setRevision(1).build();
            policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty()));

            // THEN: the write model contains up-to-date revisions
            final AbstractWriteModel writeModel = sinkProbe.expectNext().get(0);
            sinkProbe.expectComplete();
            assertThat(writeModel).isInstanceOf(ThingWriteModel.class);
            final var document = JsonObject.of(((ThingWriteModel) writeModel).getThingDocument().toJson());
            assertThat(document.getValue("_id")).contains(JsonValue.of(thingId));
            assertThat(document.getValue("policyId")).contains(JsonValue.of(policyId));
            assertThat(document.getValue("_revision")).contains(JsonValue.of(6));
            assertThat(document.getValue("__policyRev")).contains(JsonValue.of(1));
            assertThat(document.getValue("t/attributes")).contains(JsonObject.of("{\"x\":5,\"y\":6,\"z\":7}"));

            // THEN: thing is computed in the cache
            thingsProbe.expectNoMessage(FiniteDuration.Zero());
        }};
    }

    @Test
    public void onlyApplyRelevantEvents() {
        new TestKit(system) {{
            final DittoHeaders headers = DittoHeaders.empty();
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).build();
            final List<ThingEvent<?>> events = List.of(
                    // this event should be skipped because cache loader returns revision 2
                    ThingMerged.of(thingId, JsonPointer.of("attributes/v"), JsonValue.of(3), 2, null, headers, null),

                    // these events should be applied
                    ThingModified.of(thing.toBuilder()
                                    .setAttribute(JsonPointer.of("w"), JsonValue.of(4))
                                    .setAttribute(JsonPointer.of("x"), JsonValue.of(5))
                                    .build(), 3,
                            null, headers, null),
                    ThingMerged.of(thingId, JsonPointer.of("attributes/y"), JsonValue.of(6), 4, null, headers, null),
                    AttributeModified.of(thingId, JsonPointer.of("z"), JsonValue.of(7), 5, null, headers, null),
                    AttributeDeleted.of(thingId, JsonPointer.of("w"), 6, null, headers, null)
            );

            final Metadata metadata =
                    Metadata.of(thingId, 6L, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            final List<Metadata> inputMap = List.of(metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(system, streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.getScheduler());

            materializeTestProbes(underTest);

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            // WHEN: thing and policy caches are loaded
            final var sudoRetrieveThing = thingsProbe.expectMsgClass(SudoRetrieveThing.class);
            thingsProbe.reply(SudoRetrieveThingResponse.of(
                    thing.toBuilder().setRevision(2).build().toJson(FieldType.all()),
                    sudoRetrieveThing.getDittoHeaders()));
            policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
            final var policy = Policy.newBuilder(policyId).setRevision(1).build();
            policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty()));

            // THEN: the write model contains up-to-date revisions
            final AbstractWriteModel writeModel = sinkProbe.expectNext().get(0);
            sinkProbe.expectComplete();
            assertThat(writeModel).isInstanceOf(ThingWriteModel.class);
            final var document = JsonObject.of(((ThingWriteModel) writeModel).getThingDocument().toJson());
            assertThat(document.getValue("_id")).contains(JsonValue.of(thingId));
            assertThat(document.getValue("policyId")).contains(JsonValue.of(policyId));
            assertThat(document.getValue("_revision")).contains(JsonValue.of(6));
            assertThat(document.getValue("__policyRev")).contains(JsonValue.of(1));
            assertThat(document.getValue("t/attributes")).contains(JsonObject.of("{\"x\":5,\"y\":6,\"z\":7}"));

            // THEN: thing is computed in the cache
            thingsProbe.expectNoMessage(FiniteDuration.Zero());
        }};
    }

    @Test
    public void thereCanBeMultipleUpdatesPerBulk() {
        new TestKit(system) {{
            final DittoHeaders headers = DittoHeaders.empty();
            final PolicyId policyId = PolicyId.of("policy:id");
            final Thing thing = Thing.newBuilder().setPolicyId(policyId).build();
            final var policy = Policy.newBuilder(policyId).setRevision(1).build();

            final List<List<Metadata>> changeMaps = List.of(IntStream.range(1, 5).mapToObj(i -> {
                final ThingId thingId = ThingId.of("thing:" + i);
                final Thing ithThing = thing.toBuilder().setId(thingId).setRevision(i).build();
                final List<ThingEvent<?>> events = List.of(ThingModified.of(ithThing, i, null, headers, null));
                return Metadata.of(thingId, i, PolicyTag.of(policyId, 1L), null, Set.of(), events, null, null);
            }).toList());

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.parseString(
                    "stream.ask-with-retry.ask-timeout=30s"));
            final EnforcementFlow underTest = EnforcementFlow.of(system, streamConfig, thingsProbe.ref(),
                    policiesProbe.ref(), system.getScheduler());

            final var sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            policiesProbe.setAutoPilot(new TestActor.AutoPilot() {
                @Override
                public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                    sender.tell(sudoRetrievePolicyResponse, policiesProbe.ref());
                    return keepRunning();
                }
            });

            thingsProbe.setAutoPilot(new TestActor.AutoPilot() {
                @Override
                public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                    if (msg instanceof final SudoRetrieveThing command) {
                        final var thingId = (ThingId) command.getEntityId();
                        final int i = Integer.parseInt(thingId.getName());
                        final var response = SudoRetrieveThingResponse.of(
                                thing.toBuilder()
                                        .setId(thingId)
                                        .setRevision(i)
                                        .setAttribute(JsonPointer.of("x"), JsonValue.of(i))
                                        .build()
                                        .toJson(FieldType.all()),
                                command.getDittoHeaders()
                        );
                        sender.tell(response, getRef());
                    }
                    return keepRunning();
                }
            });

            materializeTestProbes(underTest, 16, 16);
            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(4);
            assertThat(sourceProbe.expectRequest()).isEqualTo(16);
            changeMaps.forEach(sourceProbe::sendNext);
            sourceProbe.sendComplete();

            final var list = sinkProbe.expectNext(FiniteDuration.apply(60, "s"));
            assertThat(list).hasSameSizeAs(changeMaps.get(0));
            sinkProbe.expectComplete();
        }};
    }

    private void materializeTestProbes(final EnforcementFlow enforcementFlow) {
        materializeTestProbes(enforcementFlow, 16, 1);
    }


    private void materializeTestProbes(final EnforcementFlow enforcementFlow, final int parallelism,
            final int bulkSize) {
        final var source = TestSource.<Collection<Metadata>>probe(system);
        final var sink = TestSink.<List<AbstractWriteModel>>probe(system);
        final var runnableGraph = enforcementFlow.create(source, parallelism, bulkSize)
                .viaMat(KillSwitches.single(), Keep.both())
                .toMat(sink, Keep.both());
        final var materializedValue = runnableGraph.run(() -> system);
        sourceProbe = materializedValue.first().first();
        sinkProbe = materializedValue.second();
    }
}
