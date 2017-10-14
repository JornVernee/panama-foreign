/*
 *  Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */
package javax.arrays.v2.nodes;

import javax.arrays.v2.A2Expr;
import javax.arrays.v2.ops.AssociativeOp;

/**
 * The superclass of all elementwise associative binary (hence trinary and
 * also ternary, till we get tired of counting) matrix-valued AST nodes.
 *
 * @param <T>
 */
public abstract class MatrixAssociative<T> extends MatrixElementwise<T, T> {
    public final AssociativeOp<T> op;

    public MatrixAssociative(AssociativeOp<T> op, A2Expr<T> x, int preferred_major_vote) {
        super(x, preferred_major_vote);
        this.op = op;
    }

    public MatrixAssociative(AssociativeOp<T> op, A2Expr<T> x) {
        this(op, x, x.preferredMajorVote());
    }

    @Override
    public AssociativeOp<T> op() {
        return op;
    }

}
