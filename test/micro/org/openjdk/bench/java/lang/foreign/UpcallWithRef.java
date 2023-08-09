/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Warmup;

import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.JNISupport;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.lookup;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })
public class UpcallWithRef extends CLayouts {

    static final Linker LINKER = Linker.nativeLinker();

    static final MethodHandle DOWNCALL_UPCALL_WITH_REF;

    static final MemorySegment UPCALL_STUB_GLOBAL_REF;
    static final MemorySegment UPCALL_STUB_KNOWN_FIELD;
    static final MemorySegment UPCALL_STUB_THREAD_LOCAL;
    static final MemorySegment UPCALL_STUB_SCOPED_VALUE;

    static {
        System.loadLibrary("UpcallWithRef");
        SymbolLookup lookup = SymbolLookup.loaderLookup();

        Function<String, MethodHandle> getMH = name -> {
            try {
                return lookup().findStatic(UpcallWithRef.class, name,
                    MethodType.methodType(void.class, MemorySegment.class));
            } catch (ReflectiveOperationException e) {
                throw new BootstrapMethodError(e);
            }
        };

        MethodHandle TARGET_GLOBAL_REF_MH = getMH.apply("targetGlobalRef");
        MethodHandle TARGET_KNOWN_FIELD_MH = getMH.apply("targetKnownField");
        MethodHandle TARGET_THREAD_LOCAL_MH = getMH.apply("targetThreadLocal");
        MethodHandle TARGET_SCOPED_VALUE_MH = getMH.apply("targetScopedValue");

        DOWNCALL_UPCALL_WITH_REF = LINKER.downcallHandle(
            lookup.find("upcall_with_ref").orElseThrow(),
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        Arena upcallStubArena = Arena.ofAuto();
        FunctionDescriptor upcallStubDesc = FunctionDescriptor.ofVoid(C_POINTER);
        UPCALL_STUB_GLOBAL_REF = LINKER.upcallStub(TARGET_GLOBAL_REF_MH, upcallStubDesc, upcallStubArena);
        UPCALL_STUB_KNOWN_FIELD = LINKER.upcallStub(TARGET_KNOWN_FIELD_MH, upcallStubDesc, upcallStubArena);
        UPCALL_STUB_THREAD_LOCAL = LINKER.upcallStub(TARGET_THREAD_LOCAL_MH, upcallStubDesc, upcallStubArena);
        UPCALL_STUB_SCOPED_VALUE = LINKER.upcallStub(TARGET_SCOPED_VALUE_MH, upcallStubDesc, upcallStubArena);
    }

    static final ThreadLocal<Widget> TL_WIDGET = new ThreadLocal<>();
    static final ScopedValue<Widget> SV_WIDGET = ScopedValue.newInstance();

    static Blackhole bh;
    static Widget knownField;

    // the global ref variants have the advantage that they can be used in a concurrent, cross-thread scenario
    // the other options can't
    @Benchmark
    public void global_ref(Blackhole bh) throws Throwable {
        UpcallWithRef.bh = bh;

        Widget w = new Widget(42);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ref = JNISupport.newGlobalRef(w, arena);
            DOWNCALL_UPCALL_WITH_REF.invokeExact(ref, UPCALL_STUB_GLOBAL_REF);
        }
    }

    @Benchmark
    public void known_field(Blackhole bh) throws Throwable {
        UpcallWithRef.bh = bh;

        Widget w = new Widget(42);
        knownField = w;

        DOWNCALL_UPCALL_WITH_REF.invokeExact(MemorySegment.NULL, UPCALL_STUB_KNOWN_FIELD);

        knownField = null;
    }

    @Benchmark
    public void thread_local(Blackhole bh) throws Throwable {
        UpcallWithRef.bh = bh;

        Widget w = new Widget(42);
        TL_WIDGET.set(w);

        DOWNCALL_UPCALL_WITH_REF.invokeExact(MemorySegment.NULL, UPCALL_STUB_THREAD_LOCAL);

        TL_WIDGET.remove();
    }

    @Benchmark
    public void scoped_value(Blackhole bh) throws Throwable {
        UpcallWithRef.bh = bh;

        Widget w = new Widget(42);
        ScopedValue.runWhere(SV_WIDGET, w, () -> {
            try {
                DOWNCALL_UPCALL_WITH_REF.invokeExact(MemorySegment.NULL, UPCALL_STUB_SCOPED_VALUE);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
    }

    // where

    record Widget(int x) {}

    private static void targetGlobalRef(MemorySegment ref) {
        bh.consume((Widget) JNISupport.resolveGlobalRef(ref));
    }

    private static void targetKnownField(MemorySegment unused) {
        bh.consume(knownField);
    }

    private static void targetThreadLocal(MemorySegment unused) {
        bh.consume(TL_WIDGET.get());
    }

    private static void targetScopedValue(MemorySegment unused) {
        bh.consume(SV_WIDGET.get());
    }
}
