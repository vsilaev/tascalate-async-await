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
package net.tascalate.async.tools.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.javaflow.spi.ResourceLoader;

class AsmxResourceLoader implements net.tascalate.asmx.plus.ResourceLoader {
    private final ResourceLoader loader;
    private final ReentrantLock extensionsLock;
    private Map<String, ReactiveExtension> extension2AsyncType1;
    private Map<String, ReactiveExtension> extension2AsyncTypeN;
    private Map<String, ReactiveExtension> asyncType2Extension;
    
    public AsmxResourceLoader(ResourceLoader loader) {
        this.loader = loader;
        extensionsLock = new ReentrantLock();
    }

    @Override
    public boolean hasResource(String name) {
        return loader.hasResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) throws IOException {
        return loader.getResourceAsStream(name);
    }
    
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return loader.getResources(name);
    }
    
    ReactiveExtension getExtension(String maybeExtensionClass, ReactiveTypeCardinality cardinality) {
        ensureExtensionsLoaded();
        switch (cardinality) {
            case ONE: return extension2AsyncType1.get(maybeExtensionClass);
            case MANY: return extension2AsyncTypeN.get(maybeExtensionClass);
        }
        throw new IllegalArgumentException("Unknown cardinality of the reactive type: " + cardinality);
    }
    
    ReactiveExtension getExtensionByReactiveType(String asyncType) {
        ensureExtensionsLoaded();
        return asyncType2Extension.get(asyncType);
    }
    
    private void ensureExtensionsLoaded() {
        extensionsLock.lock();
        try {
            if (null != asyncType2Extension) {
                return;
            }
            Map<String, ReactiveExtension> a2e = new HashMap<>();
            Map<String, ReactiveExtension> e2a1 = new HashMap<>();
            Map<String, ReactiveExtension> e2aN = new HashMap<>();
            Enumeration<URL> allResources = getResources("META-INF/async-await.def");
            while (allResources.hasMoreElements()) {
                URL resource = allResources.nextElement();
                List<ReactiveExtension> defs = parseExtensionDefinitions(resource);
                defs.forEach(def -> {
                   ReactiveExtension existing = a2e.get(def.reactiveType.getInternalName());
                   String reactiveType = def.reactiveType.getInternalName();
                   if (null != existing) {
                       throw new IllegalStateException(String.format(
                           "Ambiguos extension definition for reactive type %s. Confilcting resources:\n%s\n%s ", 
                           reactiveType, existing.declaringResource, def.declaringResource));
                   }
                   a2e.put(reactiveType, def);
                   Map<String, ReactiveExtension> target;
                   switch (def.cardinality) {
                       case ONE: target = e2a1; break;
                       case MANY: target = e2aN; break;
                       default: throw new IllegalStateException("Unknown reactive type cardinality in definition: " + def.cardinality);
                   }
                   target.put(def.implementationInternalName(), def);
                });
            }
            asyncType2Extension = Collections.unmodifiableMap(a2e);
            extension2AsyncType1 = Collections.unmodifiableMap(e2a1);
            extension2AsyncTypeN = Collections.unmodifiableMap(e2aN);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            extensionsLock.unlock();
        }
    }
    

    private static List<ReactiveExtension> parseExtensionDefinitions(URL url) throws IOException {
        List<ReactiveExtension> result = new ArrayList<>(); 
        try (BufferedReader reader = new LineNumberReader(new InputStreamReader(url.openStream()))) {
            String s; 
            int lineIdx = 0;
            while (null != (s = reader.readLine())) {
                if (s.length() == 0 || s.startsWith("#")) {
                    continue;
                }
                result.add(ReactiveExtension.parse(s, url, lineIdx++));
            }
        }
        return result;
    }
}
