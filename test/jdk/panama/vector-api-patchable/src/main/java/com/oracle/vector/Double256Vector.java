/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * or visit www.oracle.com if you need additional information or have
 * questions.
 */

package com.oracle.vector;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.function.*;
import java.util.stream.IntStream;
import java.util.BitSet;

import static com.oracle.vector.PatchableVecUtils.*;

class Double256Vector extends DoubleVector<Shapes.S256Bit> {

    private static final int LENGTH = 4;

    //Singleton Stub for static invocations of instance methods.
    private static final Double256Vector STUB = new Double256Vector(Long4.ZERO);
    private static final Species<Double, Shapes.S256Bit> SPECIES = new Double256Vector.Double256VectorSpecies();

    //Constants for zero comparisons
    private static final double[] pzArray = {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
    private static final double[] nzArray = {-0f, -0f, -0f, -0f, -0f, -0f, -0f, -0f};
    private static final Double256Vector POSZERO = (Double256Vector) STUB.fromArray(pzArray, 0);
    private static final Double256Vector NEGZERO = (Double256Vector) STUB.fromArray(nzArray, 0);

    @Stable
    private final Long4 vec;

    Double256Vector(Long4 vec) {
        this.vec = vec;
    }

    private Double256Vector(double[] ary) {
        this.vec = PatchableVecUtils.long4FromDoubleArray(ary, 0);
    }

    @Override
    public Vector<Double, Shapes.S256Bit> fromArray(double[] ary, int offset) {
        return new Double256Vector(PatchableVecUtils.long4FromDoubleArray(ary, offset));
    }

    @Override
    @ForceInline
    public void intoArray(double[] ary, int offset) {
        PatchableVecUtils.long4ToDoubleArray(ary, offset, this.vec);
    }

    @Override
    public Long2 toLong2() {
        return PatchableVecUtils.vextractf128(0, this.vec);
    }

    @Override
    public Long4 toLong4() {
        return this.vec;
    }

    @Override
    public Long8 toLong8() {
        return Long8.make(this.vec.extract(0), this.vec.extract(1),
                this.vec.extract(2), this.vec.extract(3), 0L, 0L, 0L, 0L);
    }

    @Override
    public Species<Double, Shapes.S256Bit> species() {
        return SPECIES;
    }

    @Override
    public Double getElement(int index) {
        if (index >= this.length()) throw new IllegalArgumentException("getElement index must be 0-3 inclusive");
        Long2 l2 = (index < 2) ? vextractf128(0, this.vec) : vextractf128(1, this.vec);
        return Double.longBitsToDouble(PatchableVecUtils.vpextrq(index, l2));
    }

    @Override
    public Vector<Double, Shapes.S256Bit> putElement(int index, Double x) {
        if (index >= this.length()) throw new IllegalArgumentException("putElement index must be 0-3 inclusive");
        double[] arr = this.toArray();
        arr[index] = x;
        return this.fromArray(arr, 0);
    }

//    @Override
    public void intoArray(byte[] a, int o) {
       PatchableVecUtils.long4ToByteArray(a,0,this.vec);
    }

    @Override
    public Vector<Integer, Shapes.S256Bit> compareEqual(Vector<Double, Shapes.S256Bit> v) {
        Double256Vector v2 = (Double256Vector) v;
        return new Int256Vector(PatchableVecUtils.vcmpeqpd(this.vec, v2.toLong4()));
    }

    @Override
    public Vector<Integer, Shapes.S256Bit> lessThan(Vector<Double, Shapes.S256Bit> v) {
        Double256Vector v2 = (Double256Vector) v;
        return new Int256Vector(PatchableVecUtils.vcmpltpd(this.vec, v2.toLong4()));
    }

    @Override
    public Vector<Integer, Shapes.S256Bit> greaterThan(Vector<Double, Shapes.S256Bit> v) {
        Double256Vector v2 = (Double256Vector) v;
        return new Int256Vector(PatchableVecUtils.vcmpgtpd(this.vec, v2.toLong4()));
    }

    @Override
    public Mask<Shapes.S256Bit> test(Predicate<Double> op) {
        boolean[] ary = new boolean[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = op.test(this.getElement(i));
        }
        return new Mask64Bit(ary);
    }

    @Override
    public Vector<Double, Shapes.S256Bit> map(UnaryOperator<Double> op) {
        double[] ary = new double[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = op.apply(this.getElement(i));
        }
        return new Double256Vector(ary);
    }

    @Override
    public Vector<Double, Shapes.S256Bit> mapWhere(Mask<Shapes.S256Bit> mask, UnaryOperator<Double> op) {
        double[] ary = new double[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = mask.getElement(i) ? op.apply(this.getElement(i)) : this.getElement(i);
        }
        return new Double256Vector(ary);
    }

    @Override
    public Vector<Double, Shapes.S256Bit> map(BinaryOperator<Double> op, Vector<Double, Shapes.S256Bit> this2) {
        Double256Vector this2c = (Double256Vector) this2;
        double[] ary = new double[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = op.apply(this.getElement(i), this2c.getElement(i));
        }
        return new Double256Vector(ary);
    }

    @Override
    public Vector<Double, Shapes.S256Bit> mapWhere(Mask<Shapes.S256Bit> mask, BinaryOperator<Double> op, Vector<Double, Shapes.S256Bit> this2) {
        Double256Vector this2c = (Double256Vector) this2;
        double[] ary = new double[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = mask.getElement(i) ? op.apply(this.getElement(i), this2c.getElement(i)) : this.getElement(i);
        }
        return new Double256Vector(ary);
    }

    @Override
    public boolean isZeros() {
        return this.vec.equals(POSZERO.toLong4()) || this.vec.equals(NEGZERO.toLong4());
    }

    @Override
    public Vector<Double, Shapes.S256Bit> neg() {
        return new Double256Vector(PatchableVecUtils.vxorpd(this.vec, NEGZERO.toLong4()));
    }

//    @Override
    public Vector<Double, Shapes.S256Bit> and(Vector<Double, Shapes.S256Bit> o) {
        Double256Vector oc = (Double256Vector) o;
        return new Double256Vector(PatchableVecUtils.vandpd(this.vec,oc.toLong4()));
    }

    @Override
    public Vector<Double, Shapes.S256Bit> xor(Vector<Double, Shapes.S256Bit> o) {
        Double256Vector oc = (Double256Vector) o;
        return new Double256Vector(PatchableVecUtils.vxorpd(this.vec, oc.toLong4()));
    }

//    @Override
    public Vector<Double, Shapes.S256Bit> sqrt() {
        return new Double256Vector(PatchableVecUtils.vsqrtpd(this.vec));
    }

    @Override
    public Vector<Double, Shapes.S256Bit> add(Vector<Double, Shapes.S256Bit> o) {
        Double256Vector oc = (Double256Vector) o;
        return new Double256Vector(PatchableVecUtils.vaddpd(this.vec, oc.toLong4()));
    }

//    @Override
    public Vector<Double, Shapes.S256Bit> sub(Vector<Double, Shapes.S256Bit> o) {
        Double256Vector oc = (Double256Vector) o;
        return new Double256Vector(PatchableVecUtils.vsubpd(this.vec,oc.toLong4()));
    }

    @Override
    public Vector<Double, Shapes.S256Bit> mul(Vector<Double, Shapes.S256Bit> o) {
        Double256Vector oc = (Double256Vector) o;
        return new Double256Vector(PatchableVecUtils.vmulpd(this.vec, oc.toLong4()));
    }

    @Override
    public <T> T reduce(Function<Double, T> mapper, BinaryOperator<T> op) {
        T accumulator = mapper.apply(this.getElement(0));
        for (int i = 1; i < this.length(); i++) {
            accumulator = op.apply(accumulator, mapper.apply(this.getElement(i)));
        }
        return accumulator;
    }

    @Override
    public Mask<Shapes.S256Bit> toMask() {
        boolean[] ary = new boolean[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = this.getElement(i) != 0f;
        }
        return new Mask64Bit(ary);
    }

    @Override
    public Double sumAll() {
        double[] res = this.toArray();
        double sum = 0f;
        for (int i = 0; i < LENGTH; i++) {
            sum += res[i];
        }
        return sum;
    }

    @Override
    public Vector<Double, Shapes.S256Bit> blend(Vector<Double, Shapes.S256Bit> b, Vector<Double, Shapes.S256Bit> mask) {
        Double256Vector bc = (Double256Vector) b;
        double[] ary = new double[this.length()];
        for (int i = 0; i < this.length(); i++) {
            ary[i] = mask.getElement(i) == 0 ? this.getElement(i) : bc.getElement(i);
        }
        return new Double256Vector(ary);
    }

    @Override
    public Vector<Double, Shapes.S256Bit> shuffle(Shuffle<Shapes.S256Bit> perm) {
        int[] p = perm.toArray();
        double[] buf = new double[this.length()];
        for (int i = 0; i < this.length(); i++) {
            buf[p[i]] = this.getElement(i);
        }
        return new Double256Vector(buf);
    }

    @Override
    public Shuffle<Shapes.S256Bit> toShuffle() {
        return new Shuffle256(this.vec);
    }

//    @Override
    public Vector<Float, Shapes.S256Bit> toFloatVector() {
        throw new UnsupportedOperationException("Double Conversions Not Supported");
    }

//    @Override
    public Vector<Integer, Shapes.S256Bit> toIntVector() {
        throw new UnsupportedOperationException("Double Conversions Not Supported");
    }

//    @Override
    public Vector<Double, Shapes.S256Bit> toDoubleVector() {
        return this;
    }

//    @Override
    public Vector<Float, Shapes.S256Bit> toFloatVectorBits() {
        return new Float256Vector(this.vec);
    }

//    @Override
    public Vector<Integer, Shapes.S256Bit> toIntVectorBits() {
        return new Int256Vector(this.vec);
    }

//    @Override
    public Vector<Double, Shapes.S256Bit> toDoubleVectorBits() {
        return this;
    }

    private static class Double256VectorSpecies implements Species<Double, Shapes.S256Bit> {

        @Override
        public Shapes.S256Bit shape() {
            return Shapes.SHAPE_256_BIT;
        }

        @Override
        public Class<Double> elementType() {
            return Double.class;
        }

        @Override
        public int elementSize() {
            return 64;
        }

        @Override
        public Function<Double, Vector<Double, Shapes.S256Bit>> fromElementFactory() {
            return (Double d) -> new Double256Vector(broadcastDoubleL4(d));
        }

        @Override
        public Vector<Double, Shapes.S256Bit> generate(IntFunction<? extends Double> generator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BiFunction<Double[], Integer, Vector<Double, Shapes.S256Bit>> fromArrayFactory() {
            return (Double[] ary, Integer offset) -> {
                double[] tmp = new double[this.length()];
                for (int i = 0; i < this.length(); i++) {
                    tmp[i] = ary[i];
                }
                return STUB.fromArray(tmp, 0);
            };
        }

//        @Override
        public BiFunction<int[], Integer, Vector<Double, Shapes.S256Bit>> fromIntArrayFactory() {
            return (int[] ints, Integer offset) -> new Double256Vector(PatchableVecUtils.long4FromIntArray(ints,offset));
        }

//        @Override
        public BiFunction<float[], Integer, Vector<Double, Shapes.S256Bit>> fromFloatArrayFactory() {
            return (float[] floats, Integer offset) -> new Double256Vector(PatchableVecUtils.long4FromFloatArray(floats,offset));
        }

//        @Override
        public BiFunction<double[], Integer, Vector<Double, Shapes.S256Bit>> fromDoubleArrayFactory() {
            return (double[] doubles, Integer offset) -> new Double256Vector(PatchableVecUtils.long4FromDoubleArray(doubles,offset));
        }

//        @Override
        public BiFunction<long[], Integer, Vector<Double, Shapes.S256Bit>> fromLongArrayFactory() {
            return (long[] longs, Integer offset) -> new Double256Vector(PatchableVecUtils.long4FromLongArray(longs,offset));
        }

        @Override
        public Shuffle<Shapes.S256Bit> iota() {
            return iota(0, 1, Integer.MAX_VALUE);
        }

        @Override
        // arithmetic sequence ((B+i*S)%M)
        public Shuffle<Shapes.S256Bit> iota(int base, int step, int modulus) {
            int arr[] = IntStream.range(0, LENGTH)
                    .map((int i) -> (base + i * step) % modulus)
                    .toArray();
            return new Shuffle256(arr);
        }

    }

    private static class Mask64Bit implements Mask<Shapes.S256Bit> {
        private boolean[] bits = new boolean[this.length()];


        Mask64Bit(boolean[] bits) {
            this.bits = bits;
        }

        @Override
        public int length() {
            return 8;
        }

        @Override
        public long toLong() {
            long res = 0;
            for (int i = 0; i < this.length(); i++) {
                res = (res << 1);
                if (bits[i]) {
                    res |= 1;
                }
            }
            return res;
        }

        @Override
        public BitSet toBitSet() {
            BitSet bs = new BitSet(this.length());
            for (int i = 0; i < this.length(); i++) {
                bs.set(i, bits[i]);
            }

            return bs;
        }

        @Override
        public boolean[] toArray() {
            return bits;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E> Vector<E, Shapes.S256Bit> toVector(Class<E> type) {
            if (type == Double.class) {
                Vector<Double, Shapes.S256Bit> v = new Double256Vector(Long4.ZERO);
                for (int i = 0; i < this.length(); i++) {
                    if (bits[i]) {
                        v = v.putElement(i, 1.0);  //NOTE: What does it mean for a float to be masked here?
                    }
                }
                return (Vector<E, Shapes.S256Bit>) v;

            }
            throw new UnsupportedOperationException();
        }
    }

    private static class Shuffle256 implements Shuffle<Shapes.S256Bit> {

        final int[] shuf;

        private Long4 vec() {
            return PatchableVecUtils.long4FromIntArray(shuf, 0);
        }

        public Shuffle256() {
            int[] nshuf = new int[this.length()];
            for (int i = 0; i < nshuf.length; i++) {
                nshuf[i] = i;
            }
            shuf = nshuf;
        }

        Shuffle256(int[] s) {
            shuf = s;
        }

        public Shuffle256(Long4 vec) {
            shuf = new int[this.length()];
            PatchableVecUtils.long4ToIntArray(shuf, 0, vec);
        }

        @Override
        public Long2 toLong2() {
            Long4 vec = vec();
            return PatchableVecUtils.vextractf128(0, vec);
        }

        @Override
        public Long4 toLong4() {
            return vec();
        }

        @Override
        public Long8 toLong8() {
            Long4 vec = vec();
            return Long8.make(vec.extract(0), vec.extract(1),
                    vec.extract(2), vec.extract(3), 0L, 0L, 0L, 0L);
        }

        @Override
        public int length() {
            return 8;
        }

        @Override
        public int[] toArray() {
            return shuf;
        }

        @Override
        public Vector<Integer, Shapes.S256Bit> toVector() {
            return new Int256Vector(vec());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E> Vector<E, Shapes.S256Bit> toVector(Class<E> type) {
            if (type == Double.class) {
                double[] buffer = new double[this.length()];
                for (int i = 0; i < shuf.length; i++) {
                    buffer[i] = (double) shuf[i];
                }
                return (Vector<E, Shapes.S256Bit>) new Double256Vector(buffer);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }
}
