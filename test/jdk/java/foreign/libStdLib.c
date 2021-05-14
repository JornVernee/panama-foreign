/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
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
 *
 */

#include <stdio.h>
#include <time.h>

#ifdef _WIN64
#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif

enum Func {
  F_PRINTF = 0,
  F_VPRINTF = 1,
  F_GMTIME = 2
};

EXPORT void* get_ptr(int func) {
    switch(func) {
        case F_PRINTF:  return &printf;
        case F_VPRINTF: return &vprintf;
        case F_GMTIME:  return &gmtime;
    }
    return NULL;
}
