/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import org.apache.solr.client.solrj.SolrQuery;
import org.dspace.core.Context;

/**
 * This plugin filters out items based on access_status_filter and fulltext matches.
 * <p>
 * If the item access_status_filter is embargo, restricted, or unknown, and the user's query matches fulltext,
 * the item is removed from the result.
 */
public class SolrServiceAccessStatusFulltextFilterPlugin implements SolrServiceSearchPlugin {

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery) {

        final String userQuery = discoveryQuery.getQuery();

        if (userQuery != null && !userQuery.trim().isEmpty() && !userQuery.equals("*:*")) {
            String fullTextFilter =
                "-((access_status_filter:embargo OR access_status_filter:restricted OR " +
                    "access_status_filter:unknown) AND fulltext:(" + userQuery + "))";
            solrQuery.addFilterQuery(fullTextFilter);
        }
    }
}
