package net.Realism.content.graph.v2;

public enum SignalKind {
    NONE, ENTRY, CHAIN;

    public byte toByte() {
        return (byte) ordinal();
    }

    public static SignalKind fromByte(byte b) {
        SignalKind[] values = values();
        return b >= 0 && b < values.length ? values[b] : NONE;
    }
}
