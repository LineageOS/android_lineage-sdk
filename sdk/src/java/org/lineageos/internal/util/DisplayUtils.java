/*
 * Copyright (C) 2017 The LineageOS Project
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
 * limitations under the License.
 */

package org.lineageos.internal.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TypeEvaluator;
import android.opengl.Matrix;
import android.util.Log;
import android.view.animation.AnimationUtils;

import com.android.server.LocalServices;
import com.android.server.display.DisplayTransformManager;

import java.util.Arrays;

public final class DisplayUtils {
    private static final String TAG = "DisplayUtils";

    private DisplayUtils() {
        // This class is not supposed to be instantiated
    }
    
    private void writeMatrix(float[] array, int matrixLevel) {
        final DisplayTransformManager dtm = LocalServices.getService(DisplayTransformManager.class);
        float red = array[0];
        float green = array[1];
        float blue = array[2];
        float alpha = 1; //TODO: Allow setting this as well

        /**
         * The transformation matrix, used to apply the transformation onto a frame.
         */
        final float[] newMatrix = new float[] {
            red,      0,        0,        0,
            0,        green,    0,        0,
            0,        0,        blue,     0,
            0,        0,        0,        alpha
        };

        /**
         * The identity matrix, used if one of the given matrices is {@code null}.
         */
        final float[] MATRIX_IDENTITY = new float[16];
        {
            Matrix.setIdentityM(MATRIX_IDENTITY, 0);
        }

        /**
         * Evaluator used to animate color matrix transitions.
         */
        final ColorMatrixEvaluator COLOR_MATRIX_EVALUATOR = new ColorMatrixEvaluator();

        final float[] from = dtm.getColorMatrix(matrixLevel);
        final float[] to = newMatrix;

        // Cancel the old animator if still running.
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator = ValueAnimator.ofObject(COLOR_MATRIX_EVALUATOR,
                from == null ? MATRIX_IDENTITY : from, to == null ? MATRIX_IDENTITY : to);
        mAnimator.setDuration(mContext.getResources()
                .getInteger(android.R.integer.config_longAnimTime));
        mAnimator.setInterpolator(AnimationUtils.loadInterpolator(
                mContext, android.R.interpolator.fast_out_slow_in));
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                final float[] value = (float[]) animator.getAnimatedValue();
                dtm.setColorMatrix(matrixLevel, value);
            }
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {

            private boolean mIsCancelled;

            @Override
            public void onAnimationCancel(Animator animator) {
                mIsCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (!mIsCancelled) {
                    // Ensure final color matrix is set at the end of the animation. If the
                    // animation is cancelled then don't set the final color matrix so the new
                    // animator can pick up from where this one left off.
                    dtm.setColorMatrix(matrixLevel, to);
                }
                mAnimator = null;
            }
        });
        mAnimator.start();
    }

    /**
     * Interpolates between two 4x4 color transform matrices (in column-major order).
     */
    private static class ColorMatrixEvaluator implements TypeEvaluator<float[]> {

        /**
         * Result matrix returned by {@link #evaluate(float, float[], float[])}.
         */
        private final float[] mResultMatrix = new float[16];

        @Override
        public float[] evaluate(float fraction, float[] startValue, float[] endValue) {
            for (int i = 0; i < mResultMatrix.length; i++) {
                mResultMatrix[i] = MathUtils.lerp(startValue[i], endValue[i], fraction);
            }
            return mResultMatrix;
        }
    }
}
