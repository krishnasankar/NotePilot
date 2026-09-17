package com.example.myassistant;

import org.junit.Test;

import static org.junit.Assert.*;

public class PermissionStoreTest {

    @Test
    public void testNullContextHandling() {
        // Safe default: privacy by default when context is null
        assertFalse(PermissionStore.getLocationPermission(null));
        assertFalse(PermissionStore.getEditPermission(null));

        // Setting on null context should not crash
        PermissionStore.setLocationPermission(null, true);
        PermissionStore.setEditPermission(null, true);

        assertFalse(PermissionStore.getLocationPermission(null));
        assertFalse(PermissionStore.getEditPermission(null));
    }
}
