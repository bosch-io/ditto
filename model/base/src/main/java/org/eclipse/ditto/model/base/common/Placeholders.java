/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.common;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Supports substitution of placeholders in the format {@code {{ prefix:key }}}
 * or the legacy-format {@code ${prefix.key}}.
 */
@Immutable
public final class Placeholders {

    private static final String PLACEHOLDER_GROUP_NAME = "ph";

    private static final String PLACEHOLDER_START = "{{";
    private static final String PLACEHOLDER_END = "}}";

    public static final String PLACEHOLDER_GROUP = "(?<" + PLACEHOLDER_GROUP_NAME + ">([^{ ])+)";
    public static final String ANY_NUMBER_OF_SPACES = " *";
    private static final String PLACEHOLDER_REGEX =
            Pattern.quote(PLACEHOLDER_START) // start of placeholder
                    + ANY_NUMBER_OF_SPACES // allow arbitrary number of spaces
                    + PLACEHOLDER_GROUP // the content of the placeholder
                    + ANY_NUMBER_OF_SPACES  // allow arbitrary number of spaces
                    + Pattern.quote(PLACEHOLDER_END); // end of placeholder
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(PLACEHOLDER_REGEX);

    private static final String LEGACY_PLACEHOLDER_START = "${";
    private static final String LEGACY_PLACEHOLDER_END = "}";
    private static final String LEGACY_REQUEST_SUBJECT_ID = Pattern.quote("request.subjectId");
    private static final String LEGACY_PLACEHOLDER_REGEX =
            Pattern.quote(LEGACY_PLACEHOLDER_START)
                    + "(?<" + PLACEHOLDER_GROUP_NAME + ">" + LEGACY_REQUEST_SUBJECT_ID + ")"
                    + Pattern.quote(LEGACY_PLACEHOLDER_END);
    private static final Pattern LEGACY_PLACEHOLDER_PATTERN = Pattern.compile(LEGACY_PLACEHOLDER_REGEX);

    private Placeholders() {
        throw new AssertionError();
    }

    /**
     * Checks whether the given {@code input} contains any placeholder.
     *
     * @param input the input.
     * @return {@code} true, if the input contains a placeholder.
     */
    public static boolean containsAnyPlaceholder(final CharSequence input) {
        requireNonNull(input);
        final String inputStr = input.toString();
        return inputStr.contains(PLACEHOLDER_START) || inputStr.contains(PLACEHOLDER_END) ||
                LEGACY_PLACEHOLDER_PATTERN.matcher(input).find();
    }

    /**
     * Substitutes any placeholder contained in the input.
     *
     * @param input the input.
     * @param placeholderReplacerFunction a function defining how a placeholder will be replaced. It must not return
     * null, instead it should throw a specific exception if a placeholder cannot be replaced.
     * @param unresolvedInputHandler exception handler providing a exception which is thrown when placeholders
     * remain unresolved, e.g. when brackets have the wrong order.
     * @param allowUnresolved if {@code false} this method throws an exception if there are any unresolved
     * placeholders after applying the given placeholder
     * @return the replaced input, if the input contains placeholders; the (same) input object, if no placeholders were
     * contained in the input.
     * @throws IllegalStateException if {@code placeholderReplacerFunction} returns null
     */
    public static String substitute(final String input,
            final Function<String, Optional<String>> placeholderReplacerFunction,
            final Function<String, DittoRuntimeException> unresolvedInputHandler,
            final boolean allowUnresolved) {
        requireNonNull(input);
        requireNonNull(placeholderReplacerFunction);
        requireNonNull(unresolvedInputHandler);

        // do not start expensive matching/replacing if there are no placeholders at all
        if (!containsAnyPlaceholder(input)) {
            return input;
        }

        final String maybeSubstituted = substitute(input, PLACEHOLDER_PATTERN, placeholderReplacerFunction);
        final String maybeSubstitutedWithLegacyPattern =
                substitute(maybeSubstituted, LEGACY_PLACEHOLDER_PATTERN, placeholderReplacerFunction);

        // check if the substitution really replaced all placeholders
        if (!allowUnresolved && containsAnyPlaceholder(maybeSubstitutedWithLegacyPattern)) {
            throw unresolvedInputHandler.apply(input);
        }

        return maybeSubstitutedWithLegacyPattern;
    }

    public static String substitute(final String input,
            final Function<String, Optional<String>> placeholderReplacerFunction,
            final Function<String, DittoRuntimeException> unresolvedInputHandler) {
        return substitute(input, placeholderReplacerFunction, unresolvedInputHandler, false);
    }

    private static String substitute(final String input, final Pattern pattern,
            final Function<String, Optional<String>> replacerFunction) {
        final Matcher matcher = pattern.matcher(input);
        // replace with StringBuilder with JDK9
        final AtomicReference<StringBuffer> bufferReference = new AtomicReference<>();
        while (matcher.find()) {
            final String placeholder = matcher.group(PLACEHOLDER_GROUP_NAME);
            replacerFunction.apply(placeholder)
                    .ifPresent(replacement -> matcher.appendReplacement(lazyGet(bufferReference, StringBuffer::new), replacement));
        }

        if (bufferReference.get() == null) { // no match -> return original string
            return input;
        } else { // there was at least one match -> append tail of original string
            matcher.appendTail(bufferReference.get());
            return bufferReference.get().toString();
        }
    }

    /**
     * Lazily initializes the given reference using the given initializer.
     * @param reference the reference to the actual instance of type T
     * @param initializer a supplier that initializes a new instance of T
     * @param <T> the type of the instance
     * @return the instance of type T, that is initialized on first access
     */
    private static <T> T lazyGet(final AtomicReference<T> reference, final Supplier<T> initializer) {
        T result = reference.get();
        if (result == null) {
            result = initializer.get();
            if (!reference.compareAndSet(null, result)) {
                return reference.get();
            }
        }
        return result;
    }
}
