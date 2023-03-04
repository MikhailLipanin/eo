/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 Objectionary.com
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.jcabi.log.Logger;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.yegor256.tojos.Tojo;
import com.yegor256.xsline.*;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eolang.maven.util.Home;
import org.eolang.maven.util.Rel;
import org.eolang.parser.ParsingTrain;
import org.eolang.parser.StUnhex;

/**
 * Compile binaries.
 *
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @since 0.1
 * @todo #1327:30m Now the implementation is unsuitable for solving
 *  real problems. It is necessary to pull out the rust code.
 *  This can be done in much the same way as TranspileMojo.
 *  Rust code should then be compiled into a shared library.
 */
@Mojo(
    name = "binarize",
    defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE
)
@SuppressWarnings("PMD.LongVariable")
public final class BinarizeMojo extends SafeMojo implements CompilationStep {

    /**
     * The directory where to binarize to.
     */
    public static final String DIR = "binarize";

    /**
     * Extension for compiled sources in XMIR format (XML).
     */
    static final String EXT = "xmir";
    /**
     * Target directory.
     * @checkstyle MemberNameCheck (7 lines)
     */
    @Parameter(
        required = true,
        defaultValue = "${project.build.directory}/eo-binaries"
    )
    @SuppressWarnings("PMD.UnusedPrivateField")
    private File generatedDir;
    /**
     * Parsing train with XSLs.
     */
    static final Train<Shift> TRAIN = new TrBulk<>(
        new TrClasspath<>(
            new ParsingTrain()
                .empty()
                .with(new StUnhex())
            ),
            Arrays.asList(
                "/org/eolang/maven/add_rust/add_rust.xsl"
            )
    ).back().back();


    @Override
    public void exec() throws IOException {
        final Collection<Tojo> sources = this.tojos.value().select(
            row -> row.exists(AssembleMojo.ATTR_XMIR2)
                && row.get(AssembleMojo.ATTR_SCOPE).equals(this.scope)
        );
        for (final Tojo tojo : sources) {
            final Path file = Paths.get(tojo.get(AssembleMojo.ATTR_XMIR2));
            final XML input = new XMLDocument(file);
            final String name = input.xpath("/program/@name").get(0);
            final Place place = new Place(name);
            final Path target = place.make(
                this.targetDir.toPath().resolve(BinarizeMojo.DIR),
                "rs"
            );
            final Path src = Paths.get(tojo.get(AssembleMojo.ATTR_EO));
            if (
                target.toFile().exists()
                    && target.toFile().lastModified() >= file.toFile().lastModified()
                    && target.toFile().lastModified() >= src.toFile().lastModified()
            ) {
                Logger.info(
                    this, "XMIR %s (%s) were already binarized to %s",
                    new Rel(file), name, new Rel(target)
                );
            } else {
                new Home(target).save(this.transpile(input, target), target);
            }
        }
    }

    private String transpile(
        final XML input,
        final Path target
    ) throws IOException {
        final String name = input.xpath("/program/@name").get(0);
        final Place place = new Place(name);
        final Train<Shift> trn = new SpyTrain(
            BinarizeMojo.TRAIN,
            place.make(this.targetDir.toPath().resolve(BinarizeMojo.DIR), "")
        );
        final Path dir = this.targetDir.toPath().resolve(BinarizeMojo.DIR);
        final XML passed = new Xsline(trn).pass(input);
        new Home(dir).save(passed.toString(), dir.relativize(target));
        return passed.xpath("/program/@rust").get(0);
    }
}
