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

import com.jcabi.log.Logger;
import com.jcabi.log.Supplier;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.yegor256.tojos.Tojo;
import com.yegor256.tojos.Tojos;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eolang.maven.optimization.OptCached;
import org.eolang.maven.optimization.OptSpy;
import org.eolang.maven.optimization.OptTrain;
import org.eolang.maven.optimization.Optimization;
import org.eolang.maven.util.Home;
import org.eolang.maven.util.Rel;
import org.eolang.parser.ParsingTrain;

/**
 * Optimize XML files.
 *
 * @since 0.1
 */
@Mojo(
    name = "optimize",
    defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    threadSafe = true
)
public final class OptimizeMojo extends SafeMojo {

    /**
     * The directory where to place intermediary files.
     */
    public static final String STEPS = "02-steps";

    /**
     * The directory where to transpile to.
     */
    public static final String DIR = "03-optimize";

    /**
     * Subdirectory for optimized cache.
     */
    public static final String OPTIMIZED = "optimized";

    /**
     * Track optimization steps into intermediate XML files?
     *
     * @checkstyle MemberNameCheck (7 lines)
     * @since 0.24.0
     */
    @SuppressWarnings("PMD.LongVariable")
    @Parameter(property = "eo.trackOptimizationSteps", required = true, defaultValue = "false")
    private boolean trackOptimizationSteps;

    /**
     * Whether we should fail on error.
     *
     * @checkstyle MemberNameCheck (7 lines)
     * @since 0.23.0
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(
        property = "eo.failOnError",
        defaultValue = "true")
    private boolean failOnError = true;

    /**
     * Whether we should fail on warn.
     *
     * @checkstyle MemberNameCheck (10 lines)
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(
        property = "eo.failOnWarning",
        required = true,
        defaultValue = "false"
    )
    private boolean failOnWarning;

    /**
     * EO cache directory.
     *
     * @checkstyle MemberNameCheck (7 lines)
     */
    @Parameter(property = "eo.cache")
    @SuppressWarnings("PMD.ImmutableField")
    private Path cache = Paths.get(System.getProperty("user.home")).resolve(".eo");

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void exec() throws IOException {
        final Collection<Tojo> sources = this.scopedTojos().select(
            row -> row.exists(AssembleMojo.ATTR_XMIR)
        );
        final Optimization common = this.optimization();
        final List<Supplier<Integer>> tasks = sources.stream()
            .filter(this::isOptimizationRequired)
            .map(tojo -> this.task(tojo, common))
            .collect(Collectors.toList());
        Logger.info(
            this,
            "Running %s optimizations in parallel",
            tasks.size()
        );
        final long done = tasks.parallelStream().mapToInt(Supplier::get).sum();
        if (done > 0) {
            Logger.info(
                this,
                "Optimized %d out of %d XMIR program(s)", done,
                sources.size()
            );
        } else {
            Logger.debug(this, "No XMIR programs out of %d optimized", sources.size());
        }
    }

    /**
     * Converts tojo to optimization task.
     *
     * We use {@link ClassLoader} in that method in order to load all
     * required dependencies correctly and avoid some problems with
     * loading of {@link javax.xml.transform.TransformerFactory}.
     * You can read more about that strange Java behavior in that discussions:
     *  - <a href="https://stackoverflow.com/questions/49113207/completablefuture-forkjoinpool-set-class-loader/57551188#57551188">CompletableFuture / ForkJoinPool Set Class Loader</a>
     *  - <a href="https://stackoverflow.com/questions/74708979/is-there-any-difference-between-parallelstream-and-executorservice"> Difference between parallelStream() and ExecutorService</a>
     * @param tojo Tojo that should be optimized.
     * @param common Optimization.
     * @return Optimization task.
     */
    private Supplier<Integer> task(
        final Tojo tojo,
        final Optimization common
    ) {
        final Path src = Paths.get(tojo.get(AssembleMojo.ATTR_XMIR));
        Logger.info(
            this, "Adding optimization task for %s",
            src
        );
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return () -> {
            try {
                Thread.currentThread().setContextClassLoader(loader);
                final XML optimized = this.optimization(tojo, common)
                    .apply(new XMLDocument(src));
                if (this.shouldPass(optimized)) {
                    tojo.set(
                        AssembleMojo.ATTR_XMIR2,
                        this.make(optimized, src).toAbsolutePath().toString()
                    );
                }
                return 1;
            } catch (final IOException exception) {
                throw new IllegalStateException(
                    String.format(
                        "Unable to optimize %s",
                        tojo.get(Tojos.KEY)
                    ),
                    exception
                );
            }
        };
    }

    /**
     * Checks if tojo was already optimized.
     *
     * @param tojo Tojo to check
     * @return True if optimization is required, false otherwise.
     */
    private boolean isOptimizationRequired(final Tojo tojo) {
        final Path src = Paths.get(tojo.get(AssembleMojo.ATTR_XMIR));
        boolean res = true;
        if (tojo.exists(AssembleMojo.ATTR_XMIR2)) {
            final Path tgt = Paths.get(tojo.get(AssembleMojo.ATTR_XMIR2));
            if (tgt.toFile().lastModified() >= src.toFile().lastModified()) {
                Logger.debug(
                    this, "Already optimized %s to %s",
                    new Rel(src), new Rel(tgt)
                );
                res = false;
            }
        }
        return res;
    }

    /**
     * Common optimization for all tojos.
     *
     * @return Optimization for all tojos.
     */
    private Optimization optimization() {
        Optimization opt;
        if (this.trackOptimizationSteps) {
            opt = new OptSpy(this.targetDir.toPath().resolve(OptimizeMojo.STEPS));
        } else {
            opt = new OptTrain();
        }
        if (this.failOnError) {
            opt = new OptTrain(opt, "/org/eolang/parser/fail-on-errors.xsl");
        }
        if (this.failOnWarning) {
            opt = new OptTrain(opt, "/org/eolang/parser/fail-on-warnings.xsl");
        }
        if (this.failOnError) {
            opt = new OptTrain(opt, "/org/eolang/parser/fail-on-critical.xsl");
        } else {
            opt = new OptTrain(opt, new ParsingTrain().empty());
        }
        return opt;
    }

    /**
     * Optimization for specific tojo.
     *
     * @param tojo Tojo
     * @param opt Optimization
     * @return Optimization for specific Tojo
     */
    private Optimization optimization(final Tojo tojo, final Optimization opt) {
        final Optimization res;
        if (tojo.exists(AssembleMojo.ATTR_HASH)) {
            res = new OptCached(
                opt,
                this.cache.resolve(OptimizeMojo.OPTIMIZED)
                    .resolve(tojo.get(AssembleMojo.ATTR_HASH))
            );
        } else {
            res = opt;
        }
        return res;
    }

    /**
     * Make path with optimized XML file after parsing.
     *
     * @param xml Optimized xml
     * @param file EO file
     * @return The file with optimized XMIR
     * @throws IOException If fails
     */
    private Path make(final XML xml, final Path file) throws IOException {
        final String name = new XMLDocument(file).xpath("/program/@name").get(0);
        final Place place = new Place(name);
        final Path dir = this.targetDir.toPath();
        final Path target = place.make(
            dir.resolve(OptimizeMojo.DIR), TranspileMojo.EXT
        );
        new Home(dir).save(
            xml.toString(),
            target
        );
        Logger.debug(
            this, "Optimized %s (program:%s) to %s",
            new Rel(file), name, new Rel(target)
        );
        return target;
    }

    /**
     * Should optimization steps pass without errors.
     *
     * @param xml Optimized xml
     * @return Should fail
     */
    private boolean shouldPass(final XML xml) {
        final List<XML> errors = xml.nodes("/program/errors/error");
        return errors.isEmpty() || this.failOnError;
    }
}
