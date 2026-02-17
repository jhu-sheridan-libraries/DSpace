/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.matcher.FacetEntryMatcher.defaultFacetMatchers;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.time.Period;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.matcher.PageMatcher;
import org.dspace.app.rest.matcher.SearchResultMatcher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.hamcrest.Matchers;
import org.junit.Test;

public class DiscoveryAccessStatusFulltextRestControllerIT extends AbstractControllerIntegrationTest {

    @Test
    public void discoverSearchObjectsTestWithContentInABitstream() throws Exception {
        //We turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
            .withName("Sub Community")
            .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withSubject("ExtraEntry")
            .build();

        Item publicItem2 = ItemBuilder.createItem(context, col2)
            .withTitle("Test 2")
            .withIssueDate("1990-02-13")
            .withAuthor("Smith, Maria").withAuthor("Doe, Jane")
            .withSubject("TestingForMore").withSubject("ExtraEntry")
            .build();

        Item publicItem3 = ItemBuilder.createItem(context, col2)
            .withTitle("Public item 2")
            .withIssueDate("2010-02-13")
            .withAuthor("Smith, Maria").withAuthor("Doe, Jane").withAuthor("test,test")
            .withAuthor("test2, test2").withAuthor("Maybe, Maybe")
            .withSubject("AnotherTest").withSubject("TestingForMore")
            .withSubject("ExtraEntry")
            .build();
        String bitstreamContent = "ThisIsSomeDummyText";
        //Add a bitstream to an item
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            Bitstream bitstream = BitstreamBuilder.
                createBitstream(context, publicItem1, is)
                .withName("Bitstream")
                .withMimeType("text/plain")
                .build();
        }

        //Run the filter media to make the text in the bitstream searchable through the query
        runDSpaceScript("filter-media", "-f", "-i", publicItem1.getHandle());

        context.restoreAuthSystemState();

        //** WHEN **
        //An anonymous user browses this endpoint to find the objects in the system
        //With a query stating 'ThisIsSomeDummyText'
        getClient().perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //This is the only item that should be returned with the query given
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.contains(
                SearchResultMatcher.matchOnItemName("item", "items", "Test")
            )))

            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }

    @Test
    public void discoverSearchObjectsTestWithContentInAPrivateBitstream() throws Exception {
        //We turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
            .withName("Sub Community")
            .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();

        //2. one public item that is readable by Anonymous
        Item publicItem1 = ItemBuilder.createItem(context, col1)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withSubject("ExtraEntry")
            .build();

        String bitstreamContent = "ThisIsSomeDummyText";

        //Make the group that anon doesn't have access to
        Group internalGroup = GroupBuilder.createGroup(context)
            .withName("Internal Group")
            .build();

        //Add this bitstream with the internal group as the reader group
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            Bitstream bitstream = BitstreamBuilder.
                createBitstream(context, publicItem1, is)
                .withName("Bitstream")
                .withDescription("Test Private Bitstream")
                .withMimeType("text/plain")
                .withReaderGroup(internalGroup)
                .build();
        }

        //Run the filter media to be able to search on the text in the bitstream
        runDSpaceScript("filter-media", "-f", "-i", publicItem1.getHandle());

        //Turn on the authorization again to make sure that private/inaccessible items don't get show/used
        context.restoreAuthSystemState();
        context.setCurrentUser(null);
        //** WHEN **
        //An anonymous user browses this endpoint to find the objects in the system
        //With a size 2
        getClient().perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the private bitstream doesn't show up
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "Test")
            ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }

    @Test
    public void discoverSearchObjectsTestWithContentInAPrivateBitstreamAnonymous() throws Exception {
        // ** GIVEN **
        setupItemWithPrivateBitstream();

        //** WHEN **
        //An anonymous user browses this endpoint to find the objects in the system
        //With a size 2
        getClient().perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyPrivateText"))

                //** THEN **
                //The status has to be 200 OK
                .andExpect(status().isOk())
                //The type has to be 'discover'
                .andExpect(jsonPath("$.type", is("discover")))
                //The page object needs to look like this
                .andExpect(jsonPath("$._embedded.searchResult.page", is(
                        PageMatcher.pageEntry(0, 20)
                )))
                //Make sure that the item with the private bitstream doesn't show up
                .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                    Matchers.contains(
                        SearchResultMatcher.matchOnItemName("item", "items", "TestRestrictedItem")
                ))))
                //These facets have to show up in the embedded.facets section as well with the given hasMore
                // property because we don't exceed their default limit for a hasMore true (the default is 10)
                .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
                //There always needs to be a self link available
                .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }

    @Test
    public void discoverSearchObjectsTestWithTitleAndContentInAPrivateBitstreamAnonymous() throws Exception {
        // ** GIVEN **
        setupItemWithPrivateBitstream();

        //** WHEN **
        //An anonymous user browses this endpoint to find the objects in the system
        //With a size 2
        getClient().perform(get("/api/discover/search/objects")
                .param("query", "TestRestrictedItem"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the private bitstream does show up, matches item name
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.containsInAnyOrder(
                SearchResultMatcher.matchOnItemName("item", "items", "TestRestrictedItem")
            )))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }


    @Test
    public void discoverSearchObjectsTestWithContentInAPrivateBitstreamAdmin() throws Exception {
        // ** GIVEN **
        setupItemWithPrivateBitstream();

        //** WHEN **
        //An admin user browses this endpoint to find the objects in the system
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyPrivateText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the private bitstream doesn't show up
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "TestRestrictedItem")
            ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }

    @Test
    public void discoverSearchObjectsTestWithContentInAPrivateBitstreamAuthenticatedNoAccess() throws Exception {
        // ** GIVEN **
        setupItemWithPrivateBitstream();

        //** WHEN **
        //An authenticated user browses this endpoint to find the objects in the system
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyPrivateText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the private bitstream doesn't show up
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "TestRestrictedItem")
            ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }

    @Test
    public void discoverSearchObjectsTestWithContentInAPrivateBitstreamAuthenticatedWithAccess() throws Exception {
        // ** GIVEN **
        setupItemWithPrivateBitstream(eperson);

        //** WHEN **
        //An authenticated user browses this endpoint to find the objects in the system
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyPrivateText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the private bitstream doesn't show up
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "TestRestrictedItem")
            ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;

    }

    @Test
    public void discoverSearchObjectsTestWithContentInAnEmbargoedBitstreamAnonymous() throws Exception {
        //** GIVEN **
        setupItemWithEmbargoedBitstream();

        //** WHEN **
        //An anonymous user browses this endpoint to find the objects in the system
        //With a size 2
        getClient().perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyEmbargoedText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the embargoed bitstream doesn't show up
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "TestEmbargoBitstreamItem")
                ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;
    }

    @Test
    public void discoverSearchObjectsTestWithTitleAndContentInAnEmbargoedBitstreamAnonymous() throws Exception {
        //** GIVEN **
        setupItemWithEmbargoedBitstream();

        //** WHEN **
        //An anonymous user browses this endpoint to find the objects in the system
        //With a size 2
        getClient().perform(get("/api/discover/search/objects")
                .param("query", "TestEmbargoBitstreamItem"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the embargoed bitstream does show up, search on item name
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.containsInAnyOrder(
                SearchResultMatcher.matchOnItemName("item", "items", "TestEmbargoBitstreamItem")
            )))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;
    }

    @Test
    public void discoverSearchObjectsTestWithContentInAnEmbargoedBitstreamAdmin() throws Exception {
        //** GIVEN **
        setupItemWithEmbargoedBitstream();
        String adminToken = getAuthToken(admin.getEmail(), password);

        //** WHEN **
        //An admin user browses this endpoint to find the objects in the system
        //With a size 2
        getClient(adminToken).perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyEmbargoedText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the embargoed bitstream doesn't show up
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "TestEmbargoBitstreamItem")
            ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;
    }

    @Test
    public void discoverSearchObjectsTestWithContentInAnEmbargoedBitstreamAuthenticated() throws Exception {
        //** GIVEN **
        setupItemWithEmbargoedBitstream();
        String epersonToken = getAuthToken(eperson.getEmail(), password);

        //** WHEN **
        //An authenticated user browses this endpoint to find the objects in the system
        //With a size 2
        getClient(epersonToken).perform(get("/api/discover/search/objects")
                .param("query", "ThisIsSomeDummyEmbargoedText"))

            //** THEN **
            //The status has to be 200 OK
            .andExpect(status().isOk())
            //The type has to be 'discover'
            .andExpect(jsonPath("$.type", is("discover")))
            //The page object needs to look like this
            .andExpect(jsonPath("$._embedded.searchResult.page", is(
                PageMatcher.pageEntry(0, 20)
            )))
            //Make sure that the item with the embargoed bitstream doesn't show up, search by authenticated user
            .andExpect(jsonPath("$._embedded.searchResult._embedded.objects", Matchers.not(
                Matchers.contains(
                    SearchResultMatcher.matchOnItemName("item", "items", "TestEmbargoBitstreamItem")
            ))))
            //These facets have to show up in the embedded.facets section as well with the given hasMore
            // property because we don't exceed their default limit for a hasMore true (the default is 10)
            .andExpect(jsonPath("$._embedded.facets", Matchers.containsInAnyOrder(defaultFacetMatchers)))
            //There always needs to be a self link available
            .andExpect(jsonPath("$._links.self.href", containsString("/api/discover/search/objects")))
        ;
    }

    private void setupItemWithPrivateBitstream() throws Exception {
        setupItemWithPrivateBitstream(null);
    }

    private void setupItemWithPrivateBitstream(EPerson groupMember) throws Exception {
        //We turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
            .withName("Sub Community")
            .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();

        //2. one public item that is readable by Anonymous
        Item publicItem1 = ItemBuilder.createItem(context, col1)
            .withTitle("TestRestrictedItem")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withSubject("ExtraEntry")
            .build();

        String bitstreamContent = "ThisIsSomeDummyPrivateText";

        //Make the private group
        GroupBuilder internalGroupBuilder = GroupBuilder.createGroup(context)
            .withName("Internal Group");

        if (groupMember != null) {
            internalGroupBuilder.addMember(groupMember);
        }

        Group internalGroup = internalGroupBuilder.build();

        //Add this bitstream with the internal group as the reader group
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.
                createBitstream(context, publicItem1, is)
                .withName("Bitstream")
                .withDescription("Test Private Bitstream")
                .withMimeType("text/plain")
                .withReaderGroup(internalGroup)
                .build();
        }

        //Run the filter media to be able to search on the text in the bitstream
        runDSpaceScript("filter-media", "-f", "-i", publicItem1.getHandle());

        //Turn on the authorization again to make sure that private/inaccessible items don't get show/used
        context.restoreAuthSystemState();
    }

    private void setupItemWithEmbargoedBitstream() throws Exception {
        context.turnOffAuthorisationSystem();

        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
            .withName("Sub Community")
            .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();

        //2. one public item that is readable by Anonymous
        Item publicItem1 = ItemBuilder.createItem(context, col1)
            .withTitle("TestEmbargoBitstreamItem")
            .withIssueDate("2025-01-01")
            .withAuthor("Smith, Donald")
            .withSubject("ExtraEntry")
            .build();

        String bitstreamContent = "ThisIsSomeDummyEmbargoedText";

        //Add this bitstream with an embargo set for anonymous group
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.
                createBitstream(context, publicItem1, is)
                .withName("Bitstream")
                .withDescription("Test Private Bitstream")
                .withMimeType("text/plain")
                .withEmbargoPeriod(Period.ofMonths(12))
                .build();
        }

        //Run the filter media to be able to search on the text in the bitstream
        runDSpaceScript("filter-media", "-f", "-i", publicItem1.getHandle());
        context.restoreAuthSystemState();
    }
}
