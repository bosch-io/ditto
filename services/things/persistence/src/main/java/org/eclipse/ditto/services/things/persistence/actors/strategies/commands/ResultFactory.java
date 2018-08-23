/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.headers.conditional.ETagValueGenerator;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * A factory for creating {@link CommandStrategy.Result} instances.
 */
@Immutable
final class ResultFactory {

    private ResultFactory() {
        throw new AssertionError();
    }

    static CommandStrategy.Result newMutationResult(final ThingModifyCommand command,
            final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response, final ETagEntityProvider eTagEntityProvider) {

        return new MutationResult(command, eventToPersist, response, false, false, eTagEntityProvider);
    }

    static CommandStrategy.Result newMutationResult(final ThingModifyCommand command,
            final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response, final boolean becomeCreated, final boolean becomeDeleted,
            final ETagEntityProvider eTagProvider) {

        return new MutationResult(command, eventToPersist, response, becomeCreated, becomeDeleted, eTagProvider);
    }

    static CommandStrategy.Result newErrorResult(final DittoRuntimeException dittoRuntimeException) {
        return new DittoRuntimeExceptionResult(dittoRuntimeException);
    }

    static CommandStrategy.Result newQueryResult(final Command command, @Nullable final Thing completeThing,
            final WithDittoHeaders response, @Nullable final ETagEntityProvider eTagEntityProvider) {

        return new InfoResult(command, completeThing, response, eTagEntityProvider);
    }

    static CommandStrategy.Result emptyResult() {
        return EmptyResult.INSTANCE;
    }

    static CommandStrategy.Result newFutureResult(final CompletionStage<WithDittoHeaders> futureResponse) {
        return new FutureInfoResult(futureResponse);
    }


    private static WithDittoHeaders appendETagHeaderIfProvided(final Command command,
            final WithDittoHeaders withDittoHeaders, @Nullable final Thing thing,
            @Nullable final ETagEntityProvider eTagProvider) {
        if (eTagProvider == null) {
            return withDittoHeaders;
        }

        @SuppressWarnings("unchecked")
        final Optional<Object> eTagEntityOpt = eTagProvider.determineETagEntity(command, thing);
        if (eTagEntityOpt.isPresent()) {
            final Optional<CharSequence> eTagValueOpt = ETagValueGenerator.generate(eTagEntityOpt.get());
            if (eTagValueOpt.isPresent())  {
                final CharSequence eTagValue = eTagValueOpt.get();
                final DittoHeaders newDittoHeaders = withDittoHeaders.getDittoHeaders().toBuilder()
                        .eTag(eTagValue)
                        .build();
                return withDittoHeaders.setDittoHeaders(newDittoHeaders);
            }
        }
        return withDittoHeaders;
    }


    private static final class EmptyResult implements CommandStrategy.Result {
        private static final EmptyResult INSTANCE = new EmptyResult();

        @Override
        public void apply(final CommandStrategy.Context context,
                final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer) {
            // do nothing
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " []";
        }
    }

    private static final class MutationResult implements CommandStrategy.Result {
        private final ThingModifyCommand command;
        private final ThingModifiedEvent eventToPersist;
        private final WithDittoHeaders response;
        private final boolean becomeCreated;
        private final boolean becomeDeleted;
        @Nullable
        private final ETagEntityProvider eTagProvider;

        private MutationResult(final ThingModifyCommand command, final ThingModifiedEvent eventToPersist,
                final WithDittoHeaders response, final boolean becomeCreated, final boolean becomeDeleted,
                @Nullable final ETagEntityProvider eTagProvider) {
            this.command = command;
            this.eventToPersist = eventToPersist;
            this.response = response;
            this.becomeCreated = becomeCreated;
            this.becomeDeleted = becomeDeleted;
            this.eTagProvider = eTagProvider;
        }

        @Override
        public void apply(final CommandStrategy.Context context,
                final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer) {
            persistConsumer.accept(eventToPersist, (event, resultingThing) -> {
                final WithDittoHeaders notificationResponse =
                        appendETagHeaderIfProvided(command, response, resultingThing, eTagProvider);
                notifyConsumer.accept(notificationResponse);
                if (becomeDeleted) {
                    context.getBecomeDeletedRunnable().run();
                }
                if (becomeCreated) {
                    context.getBecomeCreatedRunnable().run();
                }
            });
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [" +
                    "command=" + command +
                    ", eventToPersist=" + eventToPersist +
                    ", response=" + response +
                    ", becomeCreated=" + becomeCreated +
                    ", becomeDeleted=" + becomeDeleted +
                    ", eTagProvider=" + eTagProvider +
                    ']';
        }
    }

    private static final class InfoResult implements CommandStrategy.Result {
        private final Command command;
        private final WithDittoHeaders response;
        @Nullable
        private final Thing completeThing;
        @Nullable
        private final ETagEntityProvider eTagEntityProvider;

        private InfoResult(final Command command, @Nullable final Thing completeThing,
                final WithDittoHeaders response,
                @Nullable final ETagEntityProvider eTagEntityProvider) {

            this.command = command;
            this.completeThing = completeThing;
            this.response = response;
            this.eTagEntityProvider = eTagEntityProvider;
        }

        @Override
        public void apply(final CommandStrategy.Context context,
                final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer) {

            final WithDittoHeaders notificationResponse =
                    appendETagHeaderIfProvided(command, response, completeThing, eTagEntityProvider);
            notifyConsumer.accept(notificationResponse);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [" +
                    "command=" + command +
                    ", response=" + response +
                    ", completeThing=" + completeThing +
                    ", eTagEntityProvider=" + eTagEntityProvider +
                    ']';
        }
    }

    private static final class DittoRuntimeExceptionResult implements CommandStrategy.Result {
        private final DittoRuntimeException dittoRuntimeException;

        private DittoRuntimeExceptionResult(final DittoRuntimeException dittoRuntimeException) {
            this.dittoRuntimeException = dittoRuntimeException;
        }

        @Override
        public void apply(final CommandStrategy.Context context,
                final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer) {

            notifyConsumer.accept(dittoRuntimeException);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [" +
                    "dittoRuntimeException=" + dittoRuntimeException +
                    ']';
        }
    }

    private static final class FutureInfoResult implements CommandStrategy.Result {

        private final CompletionStage<WithDittoHeaders> futureResponse;

        private FutureInfoResult(final CompletionStage<WithDittoHeaders> futureResponse) {
            this.futureResponse = futureResponse;
        }

        @Override
        public void apply(final CommandStrategy.Context context,
                final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer) {

            futureResponse.thenAccept(notifyConsumer);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [" +
                    "futureResponse=" + futureResponse +
                    ']';
        }
    }
}
