package net.tascalate.async.core;

import org.apache.commons.javaflow.api.continuable;

abstract class AsyncMethodBody implements Runnable {

    abstract public @continuable void run();

}
