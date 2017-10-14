/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;

final class Long512Vector extends LongVector<Shapes.S512Bit> {
    static final Long512Species SPECIES = new Long512Species();

    static final Long512Vector ZERO = new Long512Vector();

    long[] vec;

    Long512Vector() {
        vec = new long[SPECIES.length()];
    }

    Long512Vector(long[] v) {
        vec = v;
    }


    // Unary operator

    @Override
    Long512Vector uOp(FUnOp f) {
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new Long512Vector(res);
    }

    @Override
    Long512Vector uOp(Mask<Long, Shapes.S512Bit> m, FUnOp f) {
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = m.getElement(i) ? f.apply(i, vec[i]) : vec[i];
        }
        return new Long512Vector(res);
    }

    // Binary operator

    @Override
    Long512Vector bOp(Vector<Long, Shapes.S512Bit> o, FBinOp f) {
        long[] res = new long[length()];
        Long512Vector v = (Long512Vector) o;
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i], v.vec[i]);
        }
        return new Long512Vector(res);
    }

    @Override
    Long512Vector bOp(Vector<Long, Shapes.S512Bit> o, Mask<Long, Shapes.S512Bit> m, FBinOp f) {
        long[] res = new long[length()];
        Long512Vector v = (Long512Vector) o;
        for (int i = 0; i < length(); i++) {
            res[i] = m.getElement(i) ? f.apply(i, vec[i], v.vec[i]) : vec[i];
        }
        return new Long512Vector(res);
    }

    // Trinary operator

    @Override
    Long512Vector tOp(Vector<Long, Shapes.S512Bit> o1, Vector<Long, Shapes.S512Bit> o2, FTriOp f) {
        long[] res = new long[length()];
        Long512Vector v1 = (Long512Vector) o1;
        Long512Vector v2 = (Long512Vector) o2;
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i], v1.vec[i], v2.vec[i]);
        }
        return new Long512Vector(res);
    }

    @Override
    Long512Vector tOp(Vector<Long, Shapes.S512Bit> o1, Vector<Long, Shapes.S512Bit> o2, Mask<Long, Shapes.S512Bit> m, FTriOp f) {
        long[] res = new long[length()];
        Long512Vector v1 = (Long512Vector) o1;
        Long512Vector v2 = (Long512Vector) o2;
        for (int i = 0; i < length(); i++) {
            res[i] = m.getElement(i) ? f.apply(i, vec[i], v1.vec[i], v2.vec[i]) : vec[i];
        }
        return new Long512Vector(res);
    }

    @Override
    long rOp(long v, FBinOp f) {
        for (int i = 0; i < length(); i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    // Binary test

    @Override
    Mask<Long, Shapes.S512Bit> bTest(Vector<Long, Shapes.S512Bit> o, FBinTest f) {
        Long512Vector v = (Long512Vector) o;
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec[i], v.vec[i]);
        }
        return new GenericMask<>(this.species(), bits);
    }

    // Foreach

    @Override
    void forEach(FUnCon f) {
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Long, Shapes.S512Bit> m, FUnCon f) {
        forEach((i, a) -> {
            if (m.getElement(i)) { f.apply(i, a); }
        });
    }


    Double512Vector toFP() {
        double[] res = new double[this.species().length()];
        for(int i = 0; i < this.species().length(); i++){
            res[i] = Double.longBitsToDouble(vec[i]);
        }
        return new Double512Vector(res);
    }

    @Override
    public Long512Vector rotateEL(int j) {
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++){
            res[j+i % length()] = vec[i];
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector rotateER(int j) {
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++){
            int z = i - j;
            if(j < 0) {
                res[length() + z] = vec[i];
            } else {
                res[z] = vec[i];
            }
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector shiftEL(int j) {
        long[] res = new long[length()];
        for (int i = 0; i < length()-j; i++) {
            res[i + j] = vec[i];
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector shiftER(int j) {
        long[] res = new long[length()];
        for (int i = 0; i < length()-j; i++){
            res[i] = vec[j];
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector shuffle(Vector<Long, Shapes.S512Bit> o, Shuffle<Long, Shapes.S512Bit> s) {
        Long512Vector v = (Long512Vector) o;
        return uOp((i, a) -> {
            int e = s.getElement(i);
            if(e > 0 && e < length()) {
                //from this
                return vec[e];
            } else if(e < length() * 2) {
                //from o
                return v.vec[e - length()];
            } else {
                throw new ArrayIndexOutOfBoundsException("Bad reordering for shuffle");
            }
        });
    }

    @Override
    public Long512Vector swizzle(Shuffle<Long, Shapes.S512Bit> s) {
        return uOp((i, a) -> {
            int e = s.getElement(i);
            if(e > 0 && e < length()) {
                return vec[e];
            } else {
                throw new ArrayIndexOutOfBoundsException("Bad reordering for shuffle");
            }
        });
    }

    @Override
    public <F, Z extends Shape<Vector<?, ?>>> Vector<F, Z> cast(Class<F> type, Z shape) {
        Vector.Species<F,Z> species = Vector.speciesInstance(type, shape);

        // Whichever is larger
        int blen = Math.max(species.bitSize(), bitSize()) / Byte.SIZE;
        ByteBuffer bb = ByteBuffer.allocate(blen);

        int limit = Math.min(species.length(), length());

        if (type == Byte.class) {
            for (int i = 0; i < limit; i++){
                bb.put(i, (byte) vec[i]);
            }
        } else if (type == Short.class) {
            for (int i = 0; i < limit; i++){
                bb.asShortBuffer().put(i, (short) vec[i]);
            }
        } else if (type == Integer.class) {
            for (int i = 0; i < limit; i++){
                bb.asIntBuffer().put(i, (int) vec[i]);
            }
        } else if (type == Long.class){
            for (int i = 0; i < limit; i++){
                bb.asLongBuffer().put(i, (long) vec[i]);
            }
        } else if (type == Float.class){
            for (int i = 0; i < limit; i++){
                bb.asFloatBuffer().put(i, (float) vec[i]);
            }
        } else if (type == Double.class){
            for (int i = 0; i < limit; i++){
                bb.asDoubleBuffer().put(i, (double) vec[i]);
            }
        } else {
            throw new UnsupportedOperationException("Bad lane type for casting.");
        }

        return species.fromByteBuffer(bb);
    }

    // Accessors

    @Override
    public long get(int i) {
        return vec[i];
    }

    @Override
    public Long512Vector with(int i, long e) {
        long[] res = vec.clone();
        res[i] = e;
        return new Long512Vector(res);
    }

    // Species

    @Override
    public Long512Species species() {
        return SPECIES;
    }

    static final class Long512Species extends LongSpecies<Shapes.S512Bit> {
        static final int BIT_SIZE = Shapes.S_512_BIT.bitSize();

        static final int LENGTH = BIT_SIZE / Long.SIZE;

        @Override
        public int bitSize() {
            return BIT_SIZE;
        }

        @Override
        public int length() {
            return LENGTH;
        }

        @Override
        public Class<Long> elementType() {
            return Long.class;
        }

        @Override
        public int elementSize() {
            return Long.SIZE;
        }

        @Override
        public Shapes.S512Bit shape() {
            return Shapes.S_512_BIT;
        }

        @Override
        Long512Vector op(FOp f) {
            long[] res = new long[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new Long512Vector(res);
        }

        @Override
        Long512Vector op(Mask<Long, Shapes.S512Bit> m, FOp f) {
            long[] res = new long[length()];
            for (int i = 0; i < length(); i++) {
                if (m.getElement(i)) {
                    res[i] = f.apply(i);
                }
            }
            return new Long512Vector(res);
        }

        // Factories

        @Override
        public Long512Vector zero() {
            return ZERO;
        }
    }
}
