/*
 * This file is part of spark.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lucko.spark.common.sampler.async;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.util.TemporaryFiles;

import one.profiler.AsyncProfiler;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Provides a bridge between spark and async-profiler.
 */
public enum AsyncProfilerAccess {
    INSTANCE;

    /** An instance of the async-profiler Java API. */
    private final AsyncProfiler profiler;

    /** If profiler is null, contains the reason why setup failed */
    private final Exception setupException;

    AsyncProfilerAccess() {
        AsyncProfiler profiler;
        Exception setupException = null;

        try {
            profiler = load();
            ensureCpuEventSupported(profiler);
        } catch (Exception e) {
            profiler = null;
            setupException = e;
        }

        this.profiler = profiler;
        this.setupException = setupException;
    }

    public AsyncProfiler getProfiler() {
        if (this.profiler == null) {
            throw new UnsupportedOperationException("async-profiler not supported", this.setupException);
        }
        return this.profiler;
    }

    public boolean checkSupported(SparkPlatform platform) {
        if (this.setupException != null) {
            platform.getPlugin().log(Level.INFO, "async-profiler engine is not supported on your system: " + this.setupException.getMessage());
            platform.getPlugin().log(Level.INFO, "Please see here for more information: https://spark.lucko.me/docs/misc/Using-async-profiler");
        }
        return this.profiler != null;
    }

    private static AsyncProfiler load() throws Exception {
        // check compatibility
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT).replace(" ", "");
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        Multimap<String, String> supported = ImmutableSetMultimap.<String, String>builder()
                .put("linux", "amd64")
                .put("macosx", "amd64")
                .put("macosx", "aarch64")
                .build();

        if (!supported.containsEntry(os, arch)) {
            throw new UnsupportedOperationException("Not supported for your os/arch: " + os + '/' + arch);
        }

        // extract the profiler binary from the spark jar file
        String resource = os + "/libasyncProfiler.so";
        URL profilerResource = AsyncProfilerAccess.class.getClassLoader().getResource(resource);
        if (profilerResource == null) {
            throw new IllegalStateException("Could not find " + resource + " in spark jar file");
        }

        Path extractPath = TemporaryFiles.create("spark-", "-libasyncProfiler.so.tmp");

        try (InputStream in = profilerResource.openStream()) {
            Files.copy(in, extractPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // get an instance of async-profiler
        try {
            return AsyncProfiler.getInstance(extractPath.toAbsolutePath().toString());
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("A runtime error occurred whilst loading the native library", e);
        }
    }

    /**
     * Checks the {@code profiler} to ensure the CPU event is supported.
     *
     * @param profiler the profiler instance
     * @throws Exception if the event is not supported
     */
    private static void ensureCpuEventSupported(AsyncProfiler profiler) throws Exception {
        String resp = profiler.execute("check,event=cpu").trim();
        if (!resp.equalsIgnoreCase("ok")) {
            throw new UnsupportedOperationException("CPU event is not supported");
        }
    }
}
