package com.example.BackendArchitectureLab.Util;

import java.io.Serializable;

public final class NullValue implements Serializable {

    public static final NullValue INSTANCE = new NullValue();

    public NullValue() {
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NullValue;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "NullValue";
    }
}
