package com.fongmi.android.tv.player;

public final class PlayerType {
    public static final int SYSTEM = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    private PlayerType() {}

    public static boolean isExo(int type) { return type == EXO; }
    public static boolean isIjk(int type) { return type == SYSTEM || type == IJK; }
}
