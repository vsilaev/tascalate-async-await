/**
 * ﻿Copyright 2015-2022 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.examples.provider;

import java.lang.invoke.MethodHandles;

import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.spi.SchedulerProviderLookup;

public class ProviderTest {

    static class Abc {
        @SchedulerProvider
        private Scheduler exec = Scheduler.sameThreadContextless();
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }
    
    static class Xyz {
        @SchedulerProvider
        public Scheduler exec() {
            return Scheduler.sameThreadContextless();
        }
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }
    
    static class BaseByField {
        @SchedulerProvider
        Scheduler baseExec = Scheduler.sameThreadContextless();

    }

    static class InheritedByField extends BaseByField {
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }

    static class BaseByMethod {
        @SchedulerProvider
        protected Scheduler baseExec() {
            return Scheduler.sameThreadContextless();
        }
    }

    static class InheritedByMethod extends BaseByMethod {
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }

    interface IntfA {
        //@SchedulerProvider //-- uncomment to see error
        Scheduler intfExec();
    }
    
    interface IntfB {
        @SchedulerProvider
        default Scheduler intfExec() {
            return Scheduler.sameThreadContextless();
        }
    }

    static class InheritedByInterfaces implements IntfA, IntfB {

        @Override
        public Scheduler intfExec() {
            return IntfB.super.intfExec();
        }
        
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
     
    }
    
    public static void main(String[] args) {
        SchedulerProviderLookup lookup = new SchedulerProviderLookup(true, true, true, false);
        new Abc().run(lookup);
        new Xyz().run(lookup);
        new InheritedByField().run(lookup);
        new InheritedByMethod().run(lookup);
        new InheritedByInterfaces().run(lookup);

    }
    
    static void tryAccessor(SchedulerProviderLookup lookup, Object o, MethodHandles.Lookup ownerLookup) {
        SchedulerProviderLookup.InstanceAccessor reader = lookup.getInstanceAccessor(ownerLookup);
        System.out.println("Class: " + o.getClass().getName());
        System.out.println("Accessor: " + reader);
        if (null != reader) {
            System.out.println("Value: " + reader.read(o));
        }
    }

}
