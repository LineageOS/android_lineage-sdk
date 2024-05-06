/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import lineageos.accessories.DataLinkType;

/**
 * Information about the link used to exchange data with an accessory.
 */
parcelable DataLinkInfo {
    /**
     * The type of the link.
     */
    DataLinkType type;

    /**
     * The ID of the accessory on this link, see DataLinkType for its format.
     */
    String id;
}
