/* Copyright (c) 2002,2003, Stefan Haustein, Oberhausen, Rhld., Germany
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The  above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE. */

package com.fsck.k9.mail.store.eas.adapter;


/** contains the WBXML constants  */


public interface Wbxml {

    int SWITCH_PAGE = 0;
    int END = 1;
    int ENTITY = 2;
    int STR_I = 3;
    int LITERAL = 4;
    int EXT_I_0 = 0x40;
    int EXT_I_1 = 0x41;
    int EXT_I_2 = 0x42;
    int PI = 0x43;
    int LITERAL_C = 0x44;
    int EXT_T_0 = 0x80;
    int EXT_T_1 = 0x81;
    int EXT_T_2 = 0x82;
    int STR_T = 0x83;
    int LITERAL_A = 0x084;
    int EXT_0 = 0x0c0;
    int EXT_1 = 0x0c1;
    int EXT_2 = 0x0c2;
    int OPAQUE = 0x0c3;
    int LITERAL_AC = 0x0c4;
}
