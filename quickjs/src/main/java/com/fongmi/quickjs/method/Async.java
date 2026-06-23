package com.fongmi.quickjs.method;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java9.util.concurrent.CompletableFuture;

public class Async {

    private final CompletableFuture<Object> future;
    private JSObject promise;
    private JSFunction then;
    private JSFunction reject;

    public static CompletableFuture<Object> run(JSObject object, String name, Object[] args) {
        return new Async().call(object, name, args);
    }

    private Async() {
        this.future = new CompletableFuture<>();
    }

    private CompletableFuture<Object> call(JSObject object, String name, Object[] args) {
        JSFunction function = null;
        try {
            function = object.getJSFunction(name);
            if (function == null) return empty();
            Object result = function.call(args);
            if (result instanceof JSObject) then((JSObject) result);
            else future.complete(result);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        } finally {
            if (function != null) function.release();
        }
        return future;
    }

    private CompletableFuture<Object> empty() {
        future.complete(null);
        return future;
    }

    private void then(JSObject result) {
        promise = result;
        then = promise.getJSFunction("then");
        reject = promise.getJSFunction("catch");
        if (then == null) {
            future.complete(promise);
            promise = null;
            cleanup();
            return;
        }
        try {
            then.call(callback);
            if (reject != null) reject.call(error);
        } catch (Throwable e) {
            future.completeExceptionally(e);
            cleanup();
        }
    }

    private final JSCallFunction callback = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            future.complete(args[0]);
            cleanup();
            return null;
        }
    };

    private final JSCallFunction error = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            future.completeExceptionally(new RuntimeException(args.length > 0 ? String.valueOf(args[0]) : "Promise rejected"));
            cleanup();
            return null;
        }
    };

    private void cleanup() {
        if (then != null) then.release();
        if (reject != null) reject.release();
        if (promise != null) promise.release();
        then = null;
        reject = null;
        promise = null;
    }
}
