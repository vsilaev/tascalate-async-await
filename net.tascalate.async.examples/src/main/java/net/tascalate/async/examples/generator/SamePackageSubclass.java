package net.tascalate.async.examples.generator;

import net.tascalate.async.examples.generator.base.BaseClass;

public class SamePackageSubclass extends BaseClass {
    protected String samePackageField = "XYZ";
    
    protected long samePackageMethod(long v) {
        return v * 1000;
    }
}
