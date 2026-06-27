package com.fongmi.quickjs.method;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java9.util.concurrent.CompletableFuture;

public class Async {

    private final CompletableFuture<Object> future;

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
            if (result instanceof JSObject) then(result);
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

    private void then(Object result) {
        JSObject promise = (JSObject) result;
        JSFunction then = promise.getJSFunction("then");
        JSFunction catchFunc = promise.getJSFunction("catch");
        try {
            if (then != null) then.call(callback);
            if (catchFunc != null) catchFunc.call(error);
            if (then == null && catchFunc == null) future.complete(promise);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        } finally {
            if (then != null) then.release();
            if (catchFunc != null) catchFunc.release();
            promise.release();
        }
    }

    private final JSCallFunction callback = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            if (args.length > 0) future.complete(args[0]);
            else future.complete(null);
            return null;
        }
    };

    private final JSCallFunction error = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            if (args.length > 0) future.completeExceptionally(new RuntimeException(String.valueOf(args[0])));
            else future.completeExceptionally(new RuntimeException("Promise rejected"));
            return null;
        }
    };
}
