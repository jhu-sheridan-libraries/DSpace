/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package edu.jhu.library.dspace.discovery.indexobject;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.dspace.access.status.DefaultAccessStatusHelper;
import org.dspace.core.Context;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.discovery.indexobject.ItemIndexFactoryImpl;

/**
 * This is an extended ItemIndexFactoryImpl for JHU. This class is needed for when changes are needed in the creation
 * and writing of the solr item document that cannot be done using solr plugins. For example, we needed to not write
 * fulltext to the solr document if the access_status_filter was not open.access, but fulltext is added to the document
 * in the writeDocument method instead of in buildDocument where the plugins are executed.
 */
public class JhuItemIndexFactoryImpl extends ItemIndexFactoryImpl {

    @Override
    public void writeDocument(Context context, IndexableItem indexableObject, SolrInputDocument solrInputDocument)
        throws SQLException, IOException, SolrServerException {
        SolrInputField field = solrInputDocument.getField("access_status_filter");
        if (field != null && DefaultAccessStatusHelper.OPEN_ACCESS.equals(field.getValue())) {
            super.writeDocument(context, indexableObject, solrInputDocument);
        } else {
            writeDocument(solrInputDocument, null);
        }
    }
}
