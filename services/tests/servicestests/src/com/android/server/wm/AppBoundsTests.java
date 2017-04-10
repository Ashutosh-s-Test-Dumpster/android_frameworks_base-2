/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.wm;

import android.app.ActivityManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Debug;
import android.view.DisplayInfo;
import org.junit.Test;

import android.platform.test.annotations.Presubmit;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test class to exercise logic related to {@link android.content.res.Configuration#appBounds}.
 *
 * Build/Install/Run:
 *  bit FrameworksServicesTests:com.android.server.wm.AppBoundsTests
 */
@SmallTest
@Presubmit
@org.junit.runner.RunWith(AndroidJUnit4.class)
public class AppBoundsTests extends WindowTestsBase {
    private Rect mParentBounds;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mParentBounds = new Rect(10 /*left*/, 30 /*top*/, 80 /*right*/, 60 /*bottom*/);
    }

    /**
     * Ensures the configuration app bounds at the root level match the app dimensions.
     */
    @Test
    public void testRootConfigurationBounds() throws Exception {
        final DisplayInfo info = sDisplayContent.getDisplayInfo();
        info.appWidth = 1024;
        info.appHeight = 768;

        final Configuration config = sWm.computeNewConfiguration(sDisplayContent.getDisplayId());
        // The bounds should always be positioned in the top left.
        assertEquals(config.appBounds.left, 0);
        assertEquals(config.appBounds.top, 0);

        // The bounds should equal the defined app width and height
        assertEquals(config.appBounds.width(), info.appWidth);
        assertEquals(config.appBounds.height(), info.appHeight);
    }

    /**
     * Ensures that bounds are clipped to their parent.
     */
    @Test
    public void testBoundsClipping() throws Exception {
        final Rect shiftedBounds = new Rect(mParentBounds);
        shiftedBounds.offset(10, 10);
        final Rect expectedBounds = new Rect(mParentBounds);
        expectedBounds.intersect(shiftedBounds);
        testStackBoundsConfiguration(null /*stackId*/, mParentBounds, shiftedBounds,
                expectedBounds);
    }

    /**
     * Ensures that empty bounds are not propagated to the configuration.
     */
    @Test
    public void testEmptyBounds() throws Exception {
        final Rect emptyBounds = new Rect();
        testStackBoundsConfiguration(null /*stackId*/, mParentBounds, emptyBounds,
                null /*ExpectedBounds*/);
    }

    /**
     * Ensures that bounds on freeform stacks are not clipped.
     */
    @Test
    public void testFreeFormBounds() throws Exception {
        final Rect freeFormBounds = new Rect(mParentBounds);
        freeFormBounds.offset(10, 10);
        testStackBoundsConfiguration(ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID,
                mParentBounds, freeFormBounds, freeFormBounds);
    }

    /**
     * Ensures that fully contained bounds are not clipped.
     */
    @Test
    public void testContainedBounds() throws Exception {
        final Rect insetBounds = new Rect(mParentBounds);
        insetBounds.inset(5, 5, 5, 5);
        testStackBoundsConfiguration(null /*stackId*/, mParentBounds, insetBounds, insetBounds);
    }

    /**
     * Ensures that full screen free form bounds are clipped
     */
    @Test
    public void testFullScreenFreeFormBounds() throws Exception {
        final Rect fullScreenBounds = new Rect(0, 0, sDisplayInfo.logicalWidth,
                sDisplayInfo.logicalHeight);
        testStackBoundsConfiguration(null /*stackId*/, mParentBounds, fullScreenBounds,
                mParentBounds);
    }


    private void testStackBoundsConfiguration(Integer stackId, Rect parentBounds, Rect bounds,
            Rect expectedConfigBounds) {
        final StackWindowController stackController = stackId != null ?
                createStackControllerOnStackOnDisplay(stackId, sDisplayContent)
                : createStackControllerOnDisplay(sDisplayContent);

        final Configuration parentConfig = sDisplayContent.getConfiguration();
        parentConfig.setAppBounds(parentBounds);

        final Configuration config = new Configuration();
        stackController.adjustConfigurationForBounds(bounds, null /*insetBounds*/,
                new Rect() /*nonDecorBounds*/, new Rect() /*stableBounds*/, false /*overrideWidth*/,
                false /*overrideHeight*/, sDisplayInfo.logicalDensityDpi, config, parentConfig);
        // Assert that both expected and actual are null or are equal to each other

        assertTrue((expectedConfigBounds == null && config.appBounds == null)
                || expectedConfigBounds.equals(config.appBounds));
    }
}
