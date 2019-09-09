/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageUtils {
    private static final String TAG = ImageUtils.class.getSimpleName();

    private static final String ASSET_URI_PREFIX = "file:///android_asset/";
    private static final int DEFAULT_IMG_QUALITY = 100;

    /**
     * Gets the Width and Height of the image
     *
     * @param inputStream The input stream of the image
     *
     * @return A point structure that holds the Width and Height (x and y)/*"
     */
    public static Point getImageDimension(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("'inputStream' cannot be null!");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        Point point = new Point(options.outWidth,options.outHeight);
        return point;
    }

    /**
     * Crops the input image and returns a new InputStream of the cropped area
     *
     * @param inputStream The input stream of the image
     * @param imageWidth Width of the input image
     * @param imageHeight Height of the input image
     * @param inputStream Desired Width
     * @param inputStream Desired Width
     *
     * @return a new InputStream of the cropped area/*"
     */
    public static InputStream cropImage(InputStream inputStream, int imageWidth, int imageHeight,
            int outWidth, int outHeight) throws IllegalArgumentException {
        if (inputStream == null){
            throw new IllegalArgumentException("inputStream cannot be null");
        }

        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    String.format("imageWidth and imageHeight must be > 0: imageWidth=%d" +
                            " imageHeight=%d", imageWidth, imageHeight));
        }

        if (outWidth <= 0 || outHeight <= 0) {
            throw new IllegalArgumentException(
                    String.format("outWidth and outHeight must be > 0: outWidth=%d" +
                            " outHeight=%d", imageWidth, outHeight));
        }

        int scaleDownSampleSize = Math.min(imageWidth / outWidth, imageHeight / outHeight);
        if (scaleDownSampleSize > 0) {
            imageWidth /= scaleDownSampleSize;
            imageHeight /= scaleDownSampleSize;
        } else {
            float ratio = (float) outWidth / outHeight;
            if (imageWidth < imageHeight * ratio) {
                outWidth = imageWidth;
                outHeight = (int) (outWidth / ratio);
            } else {
                outHeight = imageHeight;
                outWidth = (int) (outHeight * ratio);
            }
        }
        int left = (imageWidth - outWidth) / 2;
        int top = (imageHeight - outHeight) / 2;
        InputStream compressed = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            if (scaleDownSampleSize > 1) {
                options.inSampleSize = scaleDownSampleSize;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (bitmap == null) {
                return null;
            }
            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, outWidth, outHeight);
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
            if (cropped.compress(Bitmap.CompressFormat.PNG, DEFAULT_IMG_QUALITY, tmpOut)) {
                byte[] outByteArray = tmpOut.toByteArray();
                compressed = new ByteArrayInputStream(outByteArray);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return compressed;
    }
}
