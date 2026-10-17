package com.fongmi.quickjs.method;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.*;

public class AsyncTest {
    @Test public void resolvedPromiseReleasesBothUnusedChainedResults() throws Exception {
        Chain chain = new Chain(false);
        assertEquals("resolved", Async.run(chain.entry, "home", new Object[0]).get(1, TimeUnit.SECONDS));
        chain.assertReleased();
    }

    @Test public void rejectedPromiseReleasesBothUnusedChainedResults() throws Exception {
        Chain chain = new Chain(true);
        try {
            Async.run(chain.entry, "home", new Object[0]).get(1, TimeUnit.SECONDS);
            fail("Expected rejection");
        } catch (ExecutionException expected) {
            assertEquals("rejected", expected.getCause().getMessage());
        }
        chain.assertReleased();
    }

    @Test public void throwingCatchGetterReleasesAlreadyAcquiredPromiseReferences() throws Exception {
        Chain chain = new Chain(false);
        chain.promise.throwOnGet = "catch";
        try {
            Async.run(chain.entry, "home", new Object[0]).get(1, TimeUnit.SECONDS);
            fail("Expected the getter error");
        } catch (ExecutionException expected) {
            assertEquals("bad catch getter", expected.getCause().getMessage());
        }
        assertEquals(0, chain.promise.references);
        assertEquals(0, chain.then.references);
        assertEquals(0, chain.function.references);
    }

    private static final class Chain {
        final Value promise = new Value();
        final Value thenResult = new Value(), catchResult = new Value();
        final Value then, catcher, function;
        final JSObject entry;

        Chain(boolean reject) {
            then = new Value(args -> {
                if (!reject) ((JSCallFunction) args[0]).call("resolved");
                return thenResult.object;
            });
            catcher = new Value(args -> {
                if (reject) ((JSCallFunction) args[0]).call("rejected");
                return catchResult.object;
            });
            promise.properties.put("then", then.object);
            promise.properties.put("catch", catcher.object);
            function = new Value(args -> promise.object);
            Value owner = new Value();
            owner.properties.put("home", function.object);
            entry = owner.object;
        }

        void assertReleased() {
            assertEquals("Leaked result of then()", 0, thenResult.references);
            assertEquals("Leaked result of catch()", 0, catchResult.references);
            assertEquals(0, promise.references);
            assertEquals(0, then.references);
            assertEquals(0, catcher.references);
            assertEquals(0, function.references);
        }
    }

    /** Models the reference ownership of wrapper-java 3.2.3 without native QuickJS. */
    private static final class Value implements InvocationHandler {
        final Map<String, JSObject> properties = new HashMap<>();
        final Function<Object[], Object> action;
        final JSObject object;
        int references = 1;
        String throwOnGet;

        Value() { this(null); }
        Value(Function<Object[], Object> action) {
            this.action = action;
            Class<?> type = action == null ? JSObject.class : JSFunction.class;
            object = (JSObject) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, this);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getJSFunction":
                    if (args[0].equals(throwOnGet)) throw new IllegalStateException("bad catch getter");
                    return properties.get(args[0]);
                case "call": return action.apply((Object[]) args[0]);
                case "callVoid":
                    Object value = action.apply((Object[]) args[0]);
                    if (value instanceof JSObject) ((JSObject) value).release();
                    return null;
                case "release":
                    assertTrue("Reference released more than once", references > 0);
                    references--;
                    return null;
                case "hold": references++; return null;
                case "isAlive": return references > 0;
                case "getRefCount": return references;
                case "toString": return "JS fixture";
                default: throw new UnsupportedOperationException(method.getName());
            }
        }
    }
}
