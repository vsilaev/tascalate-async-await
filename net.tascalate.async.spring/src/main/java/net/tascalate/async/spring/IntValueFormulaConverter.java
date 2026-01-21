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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class IntValueFormulaConverter implements Converter<String, IntValueFormula> {

    @Override
    public IntValueFormula convert(String source) {
        if (null == source || source.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = VALUE_PATTERN.matcher(source);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid value for the numeric ratio");
        }
        IntValueFormula result;
        String s, v;
        if ((s = matcher.group(1)) != null) {
            result = describe(IntValueFormula.constant(Integer.valueOf(s)), "Constant " + s);
        } else if ((s = matcher.group(2)) != null) {
            result = describe(IntValueFormula.scale(Double.valueOf(s)), "Scale by " + s);
        } else if ((s = matcher.group(3)) != null && (v = matcher.group(4)) != null) {
            result = describe(IntValueFormula.scale(Integer.valueOf(s), Integer.valueOf(v)), "Scale by " + s + "/" + v);
        } else {
            throw new IllegalStateException();
        }
        if ((s = matcher.group(5)) != null) {
            result = describe(result.withMinValue(Integer.valueOf(s)), "MIN(" + s + ", " + result.toString() + ")");
        } 
        if ((s = matcher.group(6)) != null) {
            result = describe(result.withMaxValue(Integer.valueOf(s)), "MAX(" + s + ", " + result.toString() + ")");
        } 
        return result;
    }
    
    static IntValueFormula describe(IntValueFormula delegate, String description) {
        return new IntValueFormula() {
            
            @Override
            public int applyAsInt(int operand) {
                return delegate.applyAsInt(operand);
            }
            
            @Override
            public String toString() {
                return description;
            }
        };
    }
    
    private static final Pattern VALUE_PATTERN = Pattern.compile(
        "^\\s*(?:(\\d+)|(?:(?:\\*\\s*(\\d+(?:\\.\\d+)?))|(?:\\*\\s*(\\d+)\\s*\\/\\s*(\\d+))\\s*(?:\\:\\s*(\\d+)(?:\\s*\\:\\s*(\\d+))?)?))\\s*$", 
        Pattern.CASE_INSENSITIVE
    );
}