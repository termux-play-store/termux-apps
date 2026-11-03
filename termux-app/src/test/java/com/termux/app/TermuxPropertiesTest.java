package com.termux.app;

import android.os.Build;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class TermuxPropertiesTest {

    @Test
    public void testParseMaxSessions() {
        Assert.assertEquals(TermuxProperties.MAX_SESSIONS_DEFAULT, TermuxProperties.parseMaxSessions(null));
        Assert.assertEquals(16, TermuxProperties.parseMaxSessions("16"));
        Assert.assertEquals(12, TermuxProperties.parseMaxSessions(" 12 "));
        Assert.assertEquals(1, TermuxProperties.parseMaxSessions("1"));

        for (String invalid : new String[]{"", "0", "-1", "abc", "8.5", "99999999999"}) {
            Assert.assertEquals(TermuxProperties.MAX_SESSIONS_DEFAULT, TermuxProperties.parseMaxSessions(invalid));
        }
    }

}
