/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class AllocAndPass extends CLayouts {

    static {
        System.loadLibrary("CallOverhead");
    }

    static final MemoryLayout POINT_LAYOUT = MemoryLayout.structLayout(
        C_INT.withName("x"),
        C_INT.withName("y")
    );

    static final VarHandle VH_x = POINT_LAYOUT.varHandle(PathElement.groupElement("x"));
    static final VarHandle VH_y = POINT_LAYOUT.varHandle(PathElement.groupElement("y"));

    static final MethodHandle MH_DISTANCE = Linker.nativeLinker().downcallHandle(
        SymbolLookup.loaderLookup().find("distance").orElseThrow(),
        FunctionDescriptor.of(C_DOUBLE, POINT_LAYOUT));

    Arena recyclerArena;
    SegmentAllocator recycler;

    @Setup
    public void setup() {
        recyclerArena = Arena.ofConfined();
        recycler = SegmentAllocator.prefixAllocator(recyclerArena.allocate(POINT_LAYOUT));
    }

    @TearDown
    public void tearDown() {
        recyclerArena.close();
    }

    @Benchmark
    public double alloc_and_pass_twr() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment point = arena.allocate(POINT_LAYOUT);
            VH_x.set(point, 0L, 1);
            VH_y.set(point, 0L, 2);

            return (double) MH_DISTANCE.invokeExact(point);
        }
    }

    @Benchmark
    public double alloc_and_pass_no_twr() throws Throwable {
        Arena arena = Arena.ofConfined();
        MemorySegment point = arena.allocate(POINT_LAYOUT);
        VH_x.set(point, 0L, 1);
        VH_y.set(point, 0L, 2);

        double result = (double) MH_DISTANCE.invokeExact(point);
        arena.close();
        return result;
    }

    @Benchmark
    public double recycle_and_pass() throws Throwable {
        MemorySegment point = recycler.allocate(POINT_LAYOUT);
        VH_x.set(point, 0L, 1);
        VH_y.set(point, 0L, 2);

        return (double) MH_DISTANCE.invokeExact(point);
    }
}
