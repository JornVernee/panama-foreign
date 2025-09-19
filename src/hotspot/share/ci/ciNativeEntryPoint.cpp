/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciClassList.hpp"
#include "ci/ciNativeEntryPoint.hpp"
#include "ci/ciVMStorage.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "ci/ciArray.hpp"
#include "classfile/javaClasses.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "prims/foreignGlobals.hpp"

VMReg* get_VMReg_array(ciArray* array) {
  assert(array->element_basic_type() == T_OBJECT, "Unexpected type");

  VMReg* out = NEW_ARENA_ARRAY(CURRENT_ENV->arena(), VMReg, array->length());

  for (int i = 0; i < array->length(); i++) {
    ciConstant con = array->element_value(i);
    VMStorage vms = con.as_object()->as_vmstorage()->parse();
    VMReg reg = as_VMReg(vms);
    out[i] = reg;
  }

  return out;
}

static const char* to_C_string(oop string_oop) {
  ResourceMark rm;
  char* temp_str = java_lang_String::as_quoted_ascii(string_oop);
  size_t len = strlen(temp_str) + 1;
  char* str = (char*)CURRENT_ENV->arena()->Amalloc(len);
  strncpy(str, temp_str, len);
  return str;
}

ciNativeEntryPoint::ciNativeEntryPoint(instanceHandle h_i) : ciInstance(h_i) {
  _arg_moves = get_VMReg_array(CURRENT_ENV->get_object(jdk_internal_foreign_abi_NativeEntryPoint::argMoves(get_oop()))->as_array());
  _ret_moves = get_VMReg_array(CURRENT_ENV->get_object(jdk_internal_foreign_abi_NativeEntryPoint::returnMoves(get_oop()))->as_array());

  _shadow_space = jdk_internal_foreign_abi_NativeEntryPoint::shadow_space(get_oop());
  _needs_transition = jdk_internal_foreign_abi_NativeEntryPoint::needs_transition(get_oop());
  _needs_return_buffer = jdk_internal_foreign_abi_NativeEntryPoint::needs_return_buffer(get_oop());

  oop c2_reg_save_policy_str = jdk_internal_foreign_abi_NativeEntryPoint::c2RegSavePolicy(get_oop());
  assert(c2_reg_save_policy_str != NULL, "Must have save policy");
  _c2_reg_save_policy = to_C_string(c2_reg_save_policy_str);
}

int ciNativeEntryPoint::shadow_space() const {
  return _shadow_space;
}

VMReg* ciNativeEntryPoint::arg_moves() const {
  return _arg_moves;
}

VMReg* ciNativeEntryPoint::return_moves() const {
  return _ret_moves;
}

bool ciNativeEntryPoint::needs_transition() const {
  return _needs_transition;
}

bool ciNativeEntryPoint::needs_return_buffer() const {
  return _needs_return_buffer;
}

const char* ciNativeEntryPoint::c2_reg_save_policy() const {
  return _c2_reg_save_policy;
}
