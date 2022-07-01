/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */


#include "precompiled.hpp"
#include "opto/callGenerator.hpp"
#include "opto/graphKit.hpp"
#include "opto/runtime.hpp"
#include "opto/type.hpp"

#include <time.h>

CallGenerator* for_native_intrinsic(ciMethod* orig_callee, intptr_t target) {
  assert(orig_callee->intrinsic_id() == vmIntrinsics::_linkToNative, "expected linkToNative");
  switch (target) {
    case (intptr_t) &clock_gettime:
      return CallGenerator::for_runtime_call(orig_callee
                                             GraphKit::RC_LEAF,
                                             &clock_gettime,
                                             OptoRuntime::clock_gettime_Type(),
                                             "clock_gettime",
                                             TypeRawPtr::BOTTOM, // time spec struct is updated. Only raw memory
                                             1); // drop target addr
  }
  return nullptr;
}