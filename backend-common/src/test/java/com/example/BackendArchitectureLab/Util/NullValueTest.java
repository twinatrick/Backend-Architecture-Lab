package com.example.BackendArchitectureLab.Util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NullValueTest {

    @Test
    void instance_IsSingleton() {
        assertSame(NullValue.INSTANCE, NullValue.INSTANCE);
        assertNotNull(new NullValue());
    }

    @Test
    void equals_ReturnsTrueForAnyNullValue() {
        assertEquals(NullValue.INSTANCE, new NullValue());
    }

    @Test
    void equals_ReturnsFalseForOtherObjects() {
        assertNotEquals(NullValue.INSTANCE, "null");
        assertNotEquals(NullValue.INSTANCE, null);
    }

    @Test
    void hashCode_IsZero() {
        assertEquals(0, NullValue.INSTANCE.hashCode());
    }

    @Test
    void toString_ReturnsNullValue() {
        assertEquals("NullValue", NullValue.INSTANCE.toString());
    }
}
