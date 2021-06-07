package org.elvisdsouza.javafind.service;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.locator.ArtifactLocator;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.*;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.elvisdsouza.javafind.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final PlexusContainer plexusContainer;
    private final Indexer indexer;
    private final IndexUpdater indexUpdater;
    private final Wagon httpWagon;
    private IndexingContext centralContext;

    public SearchService() throws PlexusContainerException, ComponentLookupException, IOException {
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
        this.plexusContainer = new DefaultPlexusContainer( config );
        // lookup the indexer components from plexus
        this.indexer = plexusContainer.lookup( Indexer.class );
        this.indexUpdater = plexusContainer.lookup( IndexUpdater.class );
        // lookup wagon used to remotely fetch index
        this.httpWagon = plexusContainer.lookup( Wagon.class, "https" );

        File centralLocalCache = new File( "D:\\Projects\\Java\\testindex\\central-cache");//"target/central-cache" );
        File centralIndexDir = new File( "D:\\Projects\\Java\\testindex\\central-index");//"target/central-index" );

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add( plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "jarContent" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "maven-plugin" ) );

        // Files where local cache is (if any) and Lucene Index should be located
        centralContext =
                indexer.createIndexingContext( "central-context", "central", centralLocalCache, centralIndexDir,
                        "https://repo1.maven.org/maven2", null, true, true, indexers );

    }

    public void updateIndex() throws IOException {

        if ( true )
        {
            System.out.println( "Updating Index..." );
            System.out.println( "This might take a while on first run, so please be patient!" );
            // Create ResourceFetcher implementation to be used with IndexUpdateRequest
            // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
            TransferListener listener = new AbstractTransferListener()
            {
                public void transferStarted( TransferEvent transferEvent )
                {
                    System.out.print( "  Downloading " + transferEvent.getResource().getName() );
                }

                public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
                {
                }

                public void transferCompleted( TransferEvent transferEvent )
                {
                    System.out.println( " - Done" );
                }
            };
            ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, listener, null, null );

            Date centralContextCurrentTimestamp = centralContext.getTimestamp();
            IndexUpdateRequest updateRequest = new IndexUpdateRequest( centralContext, resourceFetcher );
            IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex( updateRequest );
            if ( updateResult.isFullUpdate() )
            {
                System.out.println( "Full update happened!" );
            }
            else if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) )
            {
                System.out.println( "No update needed, index is up to date!" );
            }
            else
            {
                System.out.println(
                        "Incremental update happened, change covered " + centralContextCurrentTimestamp + " - "
                                + updateResult.getTimestamp() + " period." );
            }

            System.out.println();
        }

    }

    public List<SearchResult> searchUserInput(String userQueryString) throws IOException {
        Query qq = indexer.constructQuery(MAVEN.ARTIFACT_ID, new UserInputSearchExpression(userQueryString));

        // Only sources
        Query sourcesQ = indexer.constructQuery(MAVEN.CLASSIFIER, new SourcedSearchExpression( "sources" ));
        BooleanQuery mainQuery = new BooleanQuery.Builder()
                .add(qq, BooleanClause.Occur.MUST)
                .add(sourcesQ, BooleanClause.Occur.MUST)
                .build();

        return search(this.indexer, "SearchQuery: "+userQueryString, mainQuery);
    }

    public List<SearchResult> search(Indexer nexusIndexer, String descr, Query q) throws IOException {
        System.out.println( "Searching for " + descr );

        FlatSearchRequest fsr = new FlatSearchRequest( q, centralContext );
        fsr.setCount(10);
        FlatSearchResponse response = nexusIndexer.searchFlat(fsr);

        System.out.println( "------" );
        System.out.println( "Total: " + response.getTotalHitsCount() );
        System.out.println();

        return response.getResults().stream().map(ai -> new SearchResult(ai)).collect(Collectors.toList());
    }

    public String searchAndDump(Indexer nexusIndexer, String descr, Query q) throws IOException {

        System.out.println( "Searching for " + descr );

        FlatSearchRequest fsr = new FlatSearchRequest( q, centralContext );
        fsr.setCount(10);
        FlatSearchResponse response = nexusIndexer.searchFlat(fsr);

        String output = "RESULTS<br/>\n=======\n<br/>";
        for ( ArtifactInfo ai : response.getResults() )
        {
            //System.out.println( ai.toString() );
            //String urlpath=centralContext.getRepositoryUrl()+centralContext.getGavCalculator().gavToPath(ai.calculateGav());
            String urlpath=centralContext.getRepositoryUrl()+centralContext.getGavCalculator()
                    .gavToPath(new Gav(ai.getGroupId(), ai.getArtifactId(), ai.getVersion(), ai.getClassifier(), ai.getFileExtension(), null, null, null, false, null, false, null ));
            urlpath = "<a href='"+urlpath+"'>"+urlpath+"</a>";
            output += ( ai.toString() +" ?"+ai.getSourcesExists() +" - "+urlpath) + "\n<br/>";
        }

        System.out.println( "------" );
        System.out.println( "Total: " + response.getTotalHitsCount() );
        System.out.println();
        return output;
    }
}
