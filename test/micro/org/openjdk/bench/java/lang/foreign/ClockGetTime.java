package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Addressable;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })
public class ClockGetTime extends CLayouts {

    static final Linker LINKER = Linker.nativeLinker();
    static final MethodHandle CLOCK_GETTIME;

    static final int CLOCK_REALTIME = 0;
    MemorySegment timeSpec;

    static {
        CLOCK_GETTIME = LINKER.downcallHandle(
            LINKER.defaultLookup().lookup("clock_gettime").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    }

    @Setup
    public void setup() {
        timeSpec = MemorySegment.allocateNative(16, MemorySession.openImplicit());
    }

    @Benchmark
    public int baseline() throws Throwable {
        return (int) CLOCK_GETTIME.invokeExact(CLOCK_REALTIME, (Addressable) timeSpec);
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview", "-XX:+UnlockDiagnosticVMOptions", "-XX:+UseNewCode" })
    public int intrinsified() throws Throwable  {
        return (int) CLOCK_GETTIME.invokeExact(CLOCK_REALTIME, (Addressable) timeSpec);
    }
}
