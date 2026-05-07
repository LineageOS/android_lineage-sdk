package org.lineageos.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import lineageos.app.ProfileGroup;
import java.lang.reflect.Modifier;

public class ProfileGroupTest {

    @Test
    public void testClassIsDeprecated() {
        Deprecated dep = ProfileGroup.class.getAnnotation(Deprecated.class);
        assertNotNull("ProfileGroup must be marked @Deprecated", dep);
    }

    @Test
    public void testClassIsPublic() {
        assertTrue(Modifier.isPublic(ProfileGroup.class.getModifiers()));
    }
}
