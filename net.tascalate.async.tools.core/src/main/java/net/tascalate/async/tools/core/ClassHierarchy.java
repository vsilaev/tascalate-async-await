/**
 * ﻿Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.tools.core;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import net.tascalate.asmx.ClassReader;
import net.tascalate.asmx.Opcodes;
import net.tascalate.asmx.Type;

import org.apache.commons.javaflow.spi.ResourceLoader;

/**
 * A class that computes the common super class of two classes without
 * actually loading them with a ClassLoader.
 * 
 * @author vsilaev
 */
public class ClassHierarchy {
    
    private final ResourceLoader loader;
    private final Map<Key, String> lookupCache;
    private final Map<TypeInfo, Reference<TypeInfo>> typesCache; 
    
    public ClassHierarchy(ResourceLoader loader) {
        this.loader = loader;
        this.lookupCache  = new HashMap<Key, String>();
        this.typesCache = new WeakHashMap<TypeInfo, Reference<TypeInfo>>();
        // Next will never be removed from the cache
        // while there is a hard-reference
        for (TypeInfo ti : SPECIAL_CLASSES) {
            typesCache.put(ti, new SoftReference<TypeInfo>(ti));
        }
    }
    
    private ClassHierarchy(ResourceLoader loader,
                           Map<Key, String> lookupCache, 
                           Map<TypeInfo, Reference<TypeInfo>> typesCache) {
        this.loader = loader;
        this.lookupCache = lookupCache;        
        this.typesCache = typesCache;
    }
    
    public ClassHierarchy shareWith(ResourceLoader resourceLoader) {
        if (resourceLoader == this.loader) {
            return this;
        }
        return new ClassHierarchy(resourceLoader, lookupCache, typesCache);
    }

    public boolean isSubClass(String type1, String type2) {
        String commonSuperClass = getCommonSuperClass(type1, type2);
        return type2.equals(commonSuperClass);
    }
    
    public boolean isSuperClass(String type1, String type2) {
        // Biased towards isSublass logic while 
        // calculateCommonSuperClass is optimized this way
        return isSubClass(type2, type1);
    }

    public String getCommonSuperClass(String type1, String type2) {
        Key key = new Key(type1, type2);
        String result;
        synchronized (lookupCache) {
            result = lookupCache.get(key);
            if (null == result) {
                result = calculateCommonSuperClass(type1, type2);
                lookupCache.put(key, result);
            }
        }
        return result;
    }
    
    Type getCommonSuperType(Type type1, Type type2) {
        return Type.getObjectType(getCommonSuperClass(type1.getInternalName(), type2.getInternalName()));
    }
    
    private String calculateCommonSuperClass(final String type1, final String type2) {
        try {
            TypeInfo info1 = getTypeInfo(type1);
            TypeInfo info2 = getTypeInfo(type2);
            // Fast check without deep loading of info2
            if (info1.isSubclassOf(info2)) {
                return type2;
            }
            // The reverse, now both will be loaded
            if (info2.isSubclassOf(info1)) {
                return type1;
            }
            // Generic (worst) case -- flattening hierarchies
            List<TypeInfo> supers1 = info1.flattenHierarchy();
            List<TypeInfo> supers2 = info2.flattenHierarchy();
            // Matching from the most specific to least specific
            for (TypeInfo a : supers1) {
                for (TypeInfo b : supers2) {
                    if (a.equals(b)) {
                        return a.name;
                    }
                }
            }
            return OBJECT.name;
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
    }
    
    TypeInfo getTypeInfo(String type) throws IOException {
        TypeInfo key = new TypeInfo(type, null, null, false);
        synchronized (typesCache) {
            Reference<TypeInfo> reference = typesCache.get(key); 
            TypeInfo value = null != reference ? reference.get() : null;
            if (null == value) {
                value = loadTypeInfo(type);
                // Same key & value
                typesCache.put(value, new SoftReference<TypeInfo>(value)); 
            }
            return value;
        }
    }

    /**
     * Returns a ClassReader corresponding to the given class or interface.
     * 
     * @param type
     *            the internal name of a class or interface.
     * @return the ClassReader corresponding to 'type'.
     * @throws IOException
     *             if the bytecode of 'type' cannot be loaded.
     */
    private TypeInfo loadTypeInfo(String type) throws IOException {
        InputStream is = loader.getResourceAsStream(type + ".class");
        try {
            ClassReader info = new ClassReader(is);
            return new TypeInfo(info.getClassName(), 
                                info.getSuperName(), 
                                info.getInterfaces(),
                                (info.getAccess() & Opcodes.ACC_INTERFACE) != 0);            
        } finally {
            is.close();
        }
    }
    
    class TypeInfo {
        final String name;
        final boolean isInterface;
        
        private String superClassName;
        private TypeInfo superClass;
        
        private String[] interfaceNames;
        private TypeInfo[] interfaces;
        
        TypeInfo(String name, String superClassName, String[] interfaceNames, boolean isInterface) {
            this.name = name;
            this.isInterface = isInterface;
            this.superClassName = superClassName;
            this.interfaceNames = null != interfaceNames ? interfaceNames : EMPTY_STRINGS;
        }
        
        synchronized TypeInfo superClass() throws IOException {
            if (null != superClassName) {
                // Not loaded yet
                superClass = getTypeInfo(superClassName);
                superClassName = null;
            }
            return superClass;
        }
        
        synchronized TypeInfo[] interfaces() throws IOException {
            if (null != interfaceNames) {
                // Not loaded yet
                int size = interfaceNames.length;
                if (size == 0) {
                    interfaces = EMPTY_TYPE_INFOS;
                } else {
                    interfaces = new TypeInfo[size];
                    for (int i = size - 1; i >= 0; i--) {
                        interfaces[i] = getTypeInfo(interfaceNames[i]);
                    }
                }
                interfaceNames = null;
            }
            return interfaces;
        }
        
        boolean isSubclassOf(TypeInfo base) throws IOException {
            String targetName = base.name;
            // Check names first to avoid loading hierarchy
            if (name.equals(targetName)) {
                return true;
            }
            synchronized (this) {
                if ((!base.isInterface) && 
                    null != superClassName && 
                    superClassName.equals(targetName)) {
                    return true;
                }
                if (base.isInterface && null != interfaceNames) {
                    for (int i = interfaceNames.length - 1; i >= 0; i--) {
                        if (interfaceNames[i].equals(targetName)) {
                            return true;
                        }
                    }
                }
            }
            TypeInfo t = superClass();
            if (null != t && t.isSubclassOf(base)) {
                return true;
            }
            // If base is interface then check interfaces
            if (base.isInterface) {
                TypeInfo[] tt = interfaces();
                for (int i = tt.length - 1; i >= 0; i--) {
                    if (tt[i].isSubclassOf(base)) {
                        return true;
                    }
                }
            }
            return false;            
        }
        
        List<TypeInfo> flattenHierarchy() throws IOException {
            Queue<TypeInfo> superclasses = new LinkedList<TypeInfo>();
            SortedSet<InterfaceEntry> interfaces = new TreeSet<InterfaceEntry>(); 
            flattenHierarchy(superclasses, interfaces, new HashSet<String>(), 0);
            
            List<TypeInfo> result = new ArrayList<TypeInfo>(superclasses.size() + 
                                                            interfaces.size() +
                                                            1);
            result.addAll(superclasses);
            result.addAll(narrow(interfaces));
            result.add(OBJECT);
            return result;
        }
        
        int flattenHierarchy(Queue<TypeInfo> superclasses, 
                             SortedSet<InterfaceEntry> interfaces,
                             Set<String> ivisited,
                             int depth) throws IOException {
            
            int strength = initialStrength();
            if (!isInterface) {
                superclasses.add(this);
            }
            // Process superclass
            TypeInfo stype = superClass();
            if (null != stype) {
                stype.flattenHierarchy(superclasses, interfaces, ivisited, depth + 1);                 
            }
            // Process interfaces;
            TypeInfo[] itypes = interfaces();
            int size = itypes.length;
            for (int i = size - 1; i >= 0; i--) {
                TypeInfo itype = itypes[i];
                // From bottom to top, so append children
                strength += itype.flattenHierarchy(null, interfaces, ivisited, depth + 1);
            }
            
            if (isInterface) {
                if (!ivisited.contains(name)) {
                    // skip if re-implemented on higher level
                    // and first appears on lower (base) level
                    interfaces.add(new InterfaceEntry(this, strength, depth));
                    ivisited.add(name);
                }
                return strength;
            } else {
                return 0;
            }
        }
        
        int initialStrength() {
            return isInterface ? 1 : 0;
        }
        
        @Override
        public String toString() {
            return name;
        }
        
        @Override
        public int hashCode() {
            return name.hashCode();
        }
        
        @Override
        public boolean equals(Object other) {
            if ((null == other) || !(other instanceof TypeInfo)) {
                return false;
            }
            return this == other || name.equals(((TypeInfo)other).name);
        }
    }
    
    private final TypeInfo OBJECT = new TypeInfo("java/lang/Object", null, null, false) {
        @Override
        TypeInfo superClass() {
            return null;
        }
        
        @Override
        TypeInfo[] interfaces() {
            return EMPTY_TYPE_INFOS;
        }
        
        @Override
        boolean isSubclassOf(TypeInfo base) {
            return equals(base);
        }
        
        @Override
        List<TypeInfo> flattenHierarchy() {
            return Collections.<TypeInfo>singletonList(this);
        }
        
        @Override
        int flattenHierarchy(Queue<TypeInfo> s, SortedSet<InterfaceEntry> i, Set<String> v, int d) {
            return 0;
        }
    };
    
    class SpecialInterfaceInfo extends TypeInfo {
        SpecialInterfaceInfo(String name, String[] interfaceNames) {
            super(name, null, interfaceNames, true);
        }
        
        @Override
        TypeInfo superClass() {
            return null;
        }
        
        @Override
        int initialStrength() {
            return 0;
        }
    }
    
    private static List<TypeInfo> narrow(SortedSet<InterfaceEntry> entries) {
        int size = entries.size();
        List<TypeInfo> result = new ArrayList<TypeInfo>(size);
        for (InterfaceEntry ie : entries) {
            result.add(ie.typeInfo);
        }
        return result;
    }
    
    static class InterfaceEntry implements Comparable<InterfaceEntry> {
        final TypeInfo typeInfo;
        final int strength;
        final int depth;
        
        InterfaceEntry(TypeInfo typeInfo, int strength, int depth) {
            this.typeInfo = typeInfo;
            this.strength = strength;
            this.depth    = depth;
        }
        
        @Override
        public int compareTo(InterfaceEntry other) {
            int delta = other.strength - this.strength;
            if (delta != 0) {
                return delta;
            }
            delta = this.depth - other.depth;
            if (delta != 0) {
                return delta;
            }
            return this.typeInfo.name.compareTo(other.typeInfo.name);
        }
    }
    
    static class Key extends SymmetricalPair<String> {
        Key(String a, String b) {
            super(a, b);
        }
    }
    
    static class SymmetricalPair<T> {
        private final T a;
        private final T b;
        SymmetricalPair(T a, T b) {
            this.a = a;
            this.b = b;
        }
        
        @Override 
        public int hashCode() {
            int hA = null == a ? 0 : a.hashCode();
            int hB = null == b ? 0 : b.hashCode();
            return Math.min(hA, hB) * 37 + Math.max(hA, hB);
        }
        
        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other.getClass() != this.getClass()) {
                return false;
            }
            @SuppressWarnings("unchecked")
            SymmetricalPair<T> that = (SymmetricalPair<T>)other;
            return same(this.a, that.a) && same(this.b, that.b) ||
                   same(this.a, that.b) && same(this.b, that.a);  
        }
        
        private static <T> boolean same(T a, T b) {
            return a == null ? b == null : a.equals(b);
        }
    }
    
    static final String[] EMPTY_STRINGS = new String[0];
    static final TypeInfo[] EMPTY_TYPE_INFOS = new TypeInfo[0];

    private final TypeInfo[] SPECIAL_CLASSES = {
        OBJECT,
        // These "special" technical interfaces are somewhat
        // messed into interface hierarchies when implemented 
        // on concrete classes.
        // So instead of Map you get Serializable, or instead of
        // Iterable collection you get Serializable or Cloneable.
        // This is not an error for this class usage, but nevertheless
        // something annoying - shift them to the end of hierarchy
        new SpecialInterfaceInfo("java/io/Externalizable", new String[] {"java/io/Serializable"}),
        new SpecialInterfaceInfo("java/io/Closeable", new String[] {"java/lang/AutoCloseable"}),
        new SpecialInterfaceInfo("java/io/Serializable", EMPTY_STRINGS),
        new SpecialInterfaceInfo("java/lang/AutoCloseable", EMPTY_STRINGS),
        new SpecialInterfaceInfo("java/lang/Cloneable", EMPTY_STRINGS),
    };
    
}