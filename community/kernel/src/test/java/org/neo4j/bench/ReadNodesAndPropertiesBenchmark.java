/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bench;

import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.impl.util.FileUtils;
import org.neo4j.tooling.GlobalGraphOperations;

public class ReadNodesAndPropertiesBenchmark implements BenchmarkCommandLineInterface.Describer,
        BenchmarkCommandLineInterface.RunBenchCase
{
    public static void main( String... args ) throws Exception
    {
        ReadNodesAndPropertiesBenchmark test = new ReadNodesAndPropertiesBenchmark();
        System.exit( new BenchmarkCommandLineInterface().evaluate( args, test, test ) );
    }

    @Override
    public void describe( PrintStream out )
    {
        out.println( "name=" + ReadNodesAndPropertiesBenchmark.class.getSimpleName() );
        out.println( "description=In a background thread, " +
                "creates single nodes (2 string properties on each). " +
                "In the main thread, read all nodes, and their properties. " +
                "Reports number of nodes 'fully read', including reading their properties." );
    }

    @Override
    public int run( BenchmarkCommandLineInterface.BasicParameters parameters ) throws Exception
    {
        Timer timer = new Timer( "Background writer", /* daemon= */true );
        File storeDir = File.createTempFile( getClass().getSimpleName(), "graphdb" );
        storeDir.delete();
        storeDir.mkdirs();

        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase( storeDir.getAbsolutePath() );
        BenchmarkResults results = new BenchmarkResults( parameters.outputResultsFile, "ms", "ms" );

        try
        {
            long startTime = System.currentTimeMillis();
            long scheduledEndTime = startTime + parameters.totalDuration;

            startBackgroundInsert( db, timer );
            Callable<ReaderResultsSample> reader = new BulkReaderWorker( db, inexhaustibleNodeIterator( db ) );

            long periodStartTime;
            while ( (periodStartTime = System.currentTimeMillis()) < scheduledEndTime )
            {
                ReaderResultsSample result = reader.call();

                long now = System.currentTimeMillis();
                long elapsedTime = now - startTime;
                long duration = now - periodStartTime;
                results.writeResult( elapsedTime,
                        "iterateNodeGet2Properties", result.iterateNodeGet2Properties, result.exceptions, duration );
            }
        }
        finally
        {
            results.close();
            timer.cancel();
            db.shutdown();
            FileUtils.deleteRecursively( storeDir );
        }
        return 0;
    }

    private static void startBackgroundInsert( final GraphDatabaseService db, Timer timer )
    {
        timer.schedule( new TimerTask()
        {
            @Override
            public void run()
            {
                Transaction tx = db.beginTx();
                try
                {
                    Node node1 = db.createNode();

                    node1.setProperty( "name", "Bob" );

                    tx.success();
                }
                finally
                {
                    tx.finish();
                }
            }
        }, 10, 200 );
    }

    static class ReaderResultsSample
    {
        int iterateNodeGet2Properties, exceptions;

        ReaderResultsSample( int iterateNodeGet2Properties, int exceptions )
        {
            this.iterateNodeGet2Properties = iterateNodeGet2Properties;
            this.exceptions = exceptions;
        }
    }

    Iterator<Node> inexhaustibleNodeIterator( GraphDatabaseService graphDb )
    {
        final GlobalGraphOperations globalGraphOperations = GlobalGraphOperations.at( graphDb );
        return new Iterator<Node>()
        {
            Iterator<Node> iterator = globalGraphOperations.getAllNodes().iterator();

            @Override
            public boolean hasNext()
            {
                if ( !iterator.hasNext() )
                {
                    iterator = globalGraphOperations.getAllNodes().iterator();
                    if ( !iterator.hasNext() )
                    {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Node next()
            {
                return iterator.next();
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    static class BulkReaderWorker implements Callable<ReaderResultsSample>
    {
        private final GraphDatabaseService graphDb;
        private final Iterator<Node> iterator;

        public BulkReaderWorker( GraphDatabaseService graphDb, Iterator<Node> iterator )
        {
            this.graphDb = graphDb;
            this.iterator = iterator;
        }

        @Override
        public ReaderResultsSample call() throws Exception
        {
            int iterateNodeGet2Properties = 0, exceptions = 0;

            Transaction tx = graphDb.beginTx();
            try
            {
                for ( int j = 0; j < 1000000; j++ )
                {
                    if ( iterator.hasNext() )
                    {
                        Node node = iterator.next();
                        try
                        {
                            for ( String propertyKey : node.getPropertyKeys() )
                            {
                                node.getProperty( propertyKey );
                            }
                        }
                        catch ( Exception e )
                        {
                            e.printStackTrace();
                            exceptions += 1;
                        }
                        iterateNodeGet2Properties++;
                    }
                }
            }
            finally
            {
                tx.finish();
            }

            return new ReaderResultsSample( iterateNodeGet2Properties, exceptions );
        }
    }


}
