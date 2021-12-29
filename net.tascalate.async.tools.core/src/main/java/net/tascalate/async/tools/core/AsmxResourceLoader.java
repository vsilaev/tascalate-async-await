package net.tascalate.async.tools.core;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.javaflow.spi.ResourceLoader;

class AsmxResourceLoader implements net.tascalate.asmx.plus.ResourceLoader {
    private final ResourceLoader loader;
    
    public AsmxResourceLoader(ResourceLoader loader) {
        this.loader = loader;
    }

    @Override
    public boolean hasResource(String name) {
        return loader.hasResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) throws IOException {
        return loader.getResourceAsStream(name);
    }
    
    
}
