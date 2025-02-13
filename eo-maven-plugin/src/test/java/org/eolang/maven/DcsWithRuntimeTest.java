/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2022 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.maven;

import java.util.Collections;
import org.apache.maven.model.Dependency;
import org.cactoos.scalar.LengthOf;
import org.cactoos.scalar.Unchecked;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link DcsWithRuntime}.
 *
 * @since 0.28.11
 */
final class DcsWithRuntimeTest {

    @Test
    void addsHardcodedVersionOfRuntimeDependency() throws Exception {
        final DcsWithRuntime dependencies = new DcsWithRuntime(
            DcsWithRuntimeTest.dependencies(),
            new Unchecked<>(DcsWithRuntimeTest::dependency)
        );
        MatcherAssert.assertThat(
            new LengthOf(dependencies).value(),
            Matchers.equalTo(2L)
        );
    }

    @Test
    void addsRemoteVersionOfRuntimeDependency() throws Exception {
        final DcsWithRuntime dependencies = new DcsWithRuntime(
            DcsWithRuntimeTest.dependencies()
        );
        MatcherAssert.assertThat(
            new LengthOf(dependencies).value(),
            Matchers.equalTo(2L)
        );
    }

    private static Iterable<Dependency> dependencies() {
        return Collections.singleton(DcsWithRuntimeTest.dependency());
    }

    private static Dependency dependency() {
        final Dependency dependency = new Dependency();
        dependency.setGroupId("org.eolang");
        dependency.setArtifactId("eo-collections");
        dependency.setVersion("0.1.0");
        return dependency;
    }
}
