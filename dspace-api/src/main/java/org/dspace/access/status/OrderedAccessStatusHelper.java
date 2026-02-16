/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.access.status;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.dspace.content.AccessStatus;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;

/**
 * Implementation of the access status helper that uses an ordered list of access statuses to enforce an order
 * of precedence when there are multiple bitstreams in the original bundle that have difference policies.
 */
public class OrderedAccessStatusHelper extends DefaultAccessStatusHelper {

    private static final List<String> ORDERED_BITSTREAM_STATUSES = List.of(EMBARGO, RESTRICTED, UNKNOWN, OPEN_ACCESS);

    /**
     * Iterate over all the bitstreams in the item's original bundle, calculate the access status for each bitstream,
     * and select the access status with the most precedence based on the ORDERED_BITSTREAM_STATUSES list. The access
     * status with the highest precedence is at index 0 in the list.
     * <p>
     * If the item Original bundle is empty, "metadata.only" is returned.
     * <p>
     * If the item is null, simply returns the "unknown" value.
     *
     * @param context     the DSpace context
     * @param item        the item to check for embargoes
     * @param threshold   the embargo threshold date
     * @param type        the type of calculation
     * @return the access status
     */
    @Override
    public AccessStatus getAccessStatusFromItem(Context context, Item item, LocalDate threshold, String type) {
        if (item == null) {
            return new AccessStatus(UNKNOWN, null);
        }

        final List<Bundle> bundles = item.getBundles(Constants.DEFAULT_BUNDLE_NAME);

        boolean noBitstreamsInBundles = bundles.stream()
            .allMatch(bundle -> bundle.getBitstreams().isEmpty());

        if (noBitstreamsInBundles) {
            return new AccessStatus(METADATA_ONLY, null);
        }

        return getAccessStatusForItemBitstreams(context, bundles, threshold, type);
    }

    private AccessStatus getAccessStatusForItemBitstreams(Context context, List<Bundle> bundles, LocalDate threshold,
                                                          String type) {
        return bundles.stream()
            .map(Bundle::getBitstreams)
            .flatMap(List::stream)
            .map(bitstream -> getAccessStatusForBitstreamSafe(context, bitstream, threshold, type))
            .min(Comparator.comparingInt(
                accessStatus -> ORDERED_BITSTREAM_STATUSES.indexOf(accessStatus.getStatus())
            ))
            .orElseGet(() -> new AccessStatus(UNKNOWN, null));
    }

    private AccessStatus getAccessStatusForBitstreamSafe(Context context, Bitstream bitstream, LocalDate threshold,
                                                         String type) {
        try {
            return getAccessStatusFromBitstream(context, bitstream, threshold, type);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
