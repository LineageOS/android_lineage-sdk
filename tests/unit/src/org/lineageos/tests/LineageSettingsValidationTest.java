package org.lineageos.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import lineageos.providers.LineageSettings;

public class LineageSettingsValidationTest {

    @Test
    public void testSystemValidatorsExist() {
        assertNotNull(LineageSettings.System.VALIDATORS);
        assertFalse(LineageSettings.System.VALIDATORS.isEmpty());
    }

    @Test
    public void testSecureValidatorsExist() {
        assertNotNull(LineageSettings.Secure.VALIDATORS);
        assertFalse(LineageSettings.Secure.VALIDATORS.isEmpty());
    }

    @Test
    public void testGlobalValidatorsExist() {
        assertNotNull(LineageSettings.Global.VALIDATORS);
        assertFalse(LineageSettings.Global.VALIDATORS.isEmpty());
    }

    @Test
    public void testMagicalTestPassingEnablerIsHidden() {
        assertNotNull(LineageSettings.System.__MAGICAL_TEST_PASSING_ENABLER);
    }
}
