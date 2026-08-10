/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.spring;

import java.util.function.IntUnaryOperator;

public interface IntValueFormula extends IntUnaryOperator {
    
    public static IntValueFormula constant(int value) {
        return operand -> value;
    }
    
    public static IntValueFormula scale(int nominator, int denominator) {
        return operand -> (operand * nominator) / denominator;
    }
    
    public static IntValueFormula scale(double factor) {
        return operand -> (int)(operand * factor);     
    }
    
    default public IntValueFormula withMinValue(int minValue) {
        return new IntValueFormula() {
            @Override
            public int applyAsInt(int operand) {
                return Math.max(IntValueFormula.this.applyAsInt(operand), minValue);
            }
        };
    }
    
    default public IntValueFormula withMaxValue(int maxValue) {
        return new IntValueFormula() {
            @Override
            public int applyAsInt(int operand) {
                return Math.min(IntValueFormula.this.applyAsInt(operand), maxValue);
            }
        };
    }
}
