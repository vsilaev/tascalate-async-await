package net.tascalate.async.examples.generator.base;

public class BaseClass {
    protected String inheritedField = "123";
    public String publicField = "ABC";
    
    protected String inheritedMethod(long v) {
        return String.valueOf(10 * v);
    }
    
    public String publicMethod() {
        return "12345";
    }
}
