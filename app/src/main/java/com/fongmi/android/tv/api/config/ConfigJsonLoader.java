package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.google.gson.*;
import java.util.function.Consumer;

/** Recursive depot fetching with injected I/O and validation; no App or runtime-state lookup. */
final class ConfigJsonLoader {
    interface Reader { String read(String url) throws Throwable; }
    private final Reader reader;
    private final Consumer<JsonObject> validator;
    ConfigJsonLoader(Reader reader, Consumer<JsonObject> validator) { this.reader=reader; this.validator=validator; }
    JsonObject load(String url,int depth) throws Throwable {
        ConfigLoadTask.check();
        return resolve(JsonParser.parseString(reader.read(url)).getAsJsonObject(),depth);
    }
    JsonObject resolve(JsonObject object,int depth) throws Throwable {
        ConfigLoadTask.check();
        if(depth>5)throw new IllegalStateException("Too many redirects.");
        if(object.has("msg"))throw new IllegalStateException(object.get("msg").getAsString());
        if(!object.has("urls")) { validator.accept(object); return object; }
        JsonArray entries=object.getAsJsonArray("urls");
        Throwable failure=null;
        for(JsonElement entry:entries) {
            try {
                String url=entry.getAsJsonObject().get("url").getAsString();
                return load(UrlUtil.fixUrl(url),depth+1);
            } catch(Throwable error) {
                ConfigLoadTask.check(); failure=error; ThreadPools.log(error,"Config depot source failed.");
            }
        }
        if(failure!=null)throw failure;
        throw new IllegalStateException("No valid config");
    }
}
