package org.elvisdsouza.javafind.service;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.artifact.GavCalculator;
import org.apache.maven.index.artifact.M2GavCalculator;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.context.NexusAnalyzer;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.search.grouping.GAGrouping;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.*;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.StringUtils;
import org.elvisdsouza.javafind.domain.JavaFindArtifact;
import org.elvisdsouza.javafind.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

    public SearchResult searchUserInput(String userQueryString) throws IOException {

        Query qq = constructSuperQuery(userQueryString);

        // Only sources
        Query sourcesQ = indexer.constructQuery(MAVEN.CLASSIFIER, new SourcedSearchExpression( "sources" ));
        BooleanQuery mainQuery = new BooleanQuery.Builder()
                .add(qq, BooleanClause.Occur.MUST)
                .add(sourcesQ, BooleanClause.Occur.MUST)
                .build();

        return searchGrouped(this.indexer, "SearchQuery: "+userQueryString, mainQuery, 10, 1);
    }

    public Query constructSuperQuery(String userQueryString) {

        /*Query query_aid = indexer.constructQuery(MAVEN.ARTIFACT_ID, new UserInputSearchExpression(userQueryString));
        Query query_gid = indexer.constructQuery(MAVEN.GROUP_ID, new UserInputSearchExpression(userQueryString));

        BooleanQuery booleanQuery = new BooleanQuery.Builder()
                .add(query_aid, BooleanClause.Occur.SHOULD)
                .add(query_gid, BooleanClause.Occur.SHOULD).build();

        return booleanQuery;*/

        String queryString = userQueryString;

        IndexerField groupIdIndexerField = selectIndexerField(MAVEN.GROUP_ID, SearchType.SCORED);
        IndexerField artifactIdIndexerField = selectIndexerField(MAVEN.ARTIFACT_ID, SearchType.SCORED);

        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{groupIdIndexerField.getKey(), artifactIdIndexerField.getKey()},
                new NexusAnalyzer());
        parser.setDefaultOperator(QueryParser.AND_OPERATOR);

        // Copied from DefaultQueryCreator
        if ( queryString.matches( ".*(\\.|-|_|/).*" ) ) {
            queryString = queryString.toLowerCase().replaceAll( "\\*", "X" ).replaceAll( "\\.|-|_|/", " " ).replaceAll( "X",
                            "*" ).replaceAll( " \\* ", "" ).replaceAll( "^\\* ", "" ).replaceAll( " \\*$", "" );
        }

        if ( !queryString.endsWith( "*" ) && !queryString.endsWith( " " ) ) {
            queryString += "*";
        }

        try {
            BooleanQuery.Builder q1Build = new BooleanQuery.Builder()
                    .add(parser.parse(queryString), BooleanClause.Occur.SHOULD);

            if ( queryString.contains( " " ) )
            {
                q1Build.add( parser.parse( "\"" + queryString + "\"" ), BooleanClause.Occur.SHOULD );
            }

            Query q2 = null;

            /*int termCount = countTerms( indexerField, userQueryString );

            // try with KW only if the processed query in qpQuery does not have spaces!
            if ( !userQueryString.contains( " " ) && termCount > 1 )
            {
                // get the KW field
                IndexerField keywordField = selectIndexerField( indexerField.getOntology(), SearchType.EXACT );

                if ( keywordField.isKeyword() )
                {
                    q2 = indexer.constructQuery( indexerField.getOntology(), keywordField, userQueryString, SearchType.SCORED );
                }
            }*/

            BooleanQuery q1 = q1Build.build();
            if ( q2 == null )
            {
                return q1;
            }
            else
            {
                BooleanQuery bq = new BooleanQuery.Builder()
                    // trick with order
                    .add( q2, BooleanClause.Occur.SHOULD )
                    .add( q1, BooleanClause.Occur.SHOULD ).build();

                return bq;
            }

        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected int countTerms( final IndexerField indexerField, final String query )
    {
        try
        {
            TokenStream ts = new NexusAnalyzer().tokenStream( indexerField.getKey(), new StringReader( query ) );
            ts.reset();

            int result = 0;

            while ( ts.incrementToken() )
            {
                result++;
            }

            ts.end();
            ts.close();

            return result;
        }
        catch ( IOException e )
        {
            // will not happen
            return 1;
        }
    }

    public IndexerField selectIndexerField( final Field field, final SearchType type )
    {
        IndexerField lastField = null;

        for ( IndexerField indexerField : field.getIndexerFields() )
        {
            lastField = indexerField;

            if ( type.matchesIndexerField( indexerField ) )
            {
                return indexerField;
            }
        }

        return lastField;
    }

    public List<JavaFindArtifact> search(Indexer nexusIndexer, String descr, Query q) throws IOException {
        System.out.println( "Searching for " + descr );

        FlatSearchRequest fsr = new FlatSearchRequest( q, centralContext );
        fsr.setCount(10);
        FlatSearchResponse response = nexusIndexer.searchFlat(fsr);

        System.out.println( "------" );
        System.out.println( "Total: " + response.getTotalHitsCount() );
        System.out.println();

        return response.getResults().stream().map(ai -> new JavaFindArtifact(ai)).collect(Collectors.toList());
    }

    public SearchResult searchGrouped(Indexer nexusIndexer, String descr, Query q,
                                                int pageSize, int pageNumber) throws IOException {
        System.out.println( " === Grouped Searching Results for -- " + descr );
        GroupedSearchRequest gsr = new GroupedSearchRequest( q, new GAGrouping(), centralContext );
        //gsr.setCount(10);
        GroupedSearchResponse response = nexusIndexer.searchGrouped(gsr);
        /*for ( Map.Entry<String, ArtifactInfoGroup> entry : response.getResults().entrySet() )
        {
            System.out.println( "* Entry KEY" + entry.getKey() );
            ArtifactInfo ai = entry.getValue().getArtifactInfos().iterator().next();
            System.out.println( "* Entry " + ai );
            System.out.println( "  Latest version:  " + ai.getVersion() );
            System.out.println( StringUtils.isBlank( ai.getDescription() )
                    ? "No description in plugin's POM."
                    : StringUtils.abbreviate( ai.getDescription(), 60 ) );
            System.out.println();
        }
        System.out.println( " === Grouped Searching ENDS === ");*/

        System.out.println( "------" );
        System.out.println( "Total Grouped: " + response.getTotalHitsCount() );
        System.out.println( "Total Individual: " + response.getReturnedHitsCount() );
        System.out.println();

        int skipRecords = pageNumber<=1? 0: (pageNumber-1)*pageSize;

        List<JavaFindArtifact> artifacts = response.getResults().values().stream().skip(skipRecords).limit(pageSize)
                .map(ai -> new JavaFindArtifact(ai)).collect(Collectors.toList());
        return new SearchResult(artifacts, response.getReturnedHitsCount());
    }

    /*public byte[] getFileBytes(JavaFindArtifact artifact) {
        try {
            String urlString = artifact.toUrlPath(centralContext);
            URL url = new URL(urlString);
            return IOUtils.toByteArray(url.openStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/

    public byte[] getFileBytes(String relFilepath) {
        try {
            String urlString = relPathToUrl(relFilepath);
            URL url = new URL(urlString);
            return IOUtils.toByteArray(url.openStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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

    private String relPathToUrl(String relFilepath) {
        GavCalculator gavCalculator = new M2GavCalculator();
        Gav gav = gavCalculator.pathToGav(relFilepath);
        return centralContext.getRepositoryUrl() + centralContext.getGavCalculator().gavToPath(gav);
    }
}
