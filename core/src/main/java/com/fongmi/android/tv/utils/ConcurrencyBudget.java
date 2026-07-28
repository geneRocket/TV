package com.fongmi.android.tv.utils;

/** Device-adaptive concurrency limits shared by application workloads. */
public final class ConcurrencyBudget {

    public final int general;
    public final int config;
    public final int loader;
    public final int search;
    public final int parse;
    public final int preload;
    public final int network;
    public final int networkPerHost;
    public final int idleConnections;

    public static ConcurrencyBudget current() {
        return forProcessors(Runtime.getRuntime().availableProcessors());
    }

    public static ConcurrencyBudget forProcessors(int processors) {
        int cores = Math.max(1, processors);
        return new ConcurrencyBudget(clamp(cores * 2, 4, 16), clamp(cores, 4, 8), clamp(cores, 2, 8), clamp(cores * 2, 4, 16), clamp(cores * 2, 4, 16), clamp(cores, 2, 4), clamp(cores * 4, 8, 32), clamp(cores * 2, 4, 8), clamp(cores * 2, 8, 16));
    }

    private ConcurrencyBudget(int general, int config, int loader, int search, int parse, int preload, int network, int networkPerHost, int idleConnections) {
        this.general = general;
        this.config = config;
        this.loader = loader;
        this.search = search;
        this.parse = parse;
        this.preload = preload;
        this.network = network;
        this.networkPerHost = networkPerHost;
        this.idleConnections = idleConnections;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
