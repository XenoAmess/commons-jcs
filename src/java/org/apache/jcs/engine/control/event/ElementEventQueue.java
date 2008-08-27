package org.apache.jcs.engine.control.event;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.engine.control.event.behavior.IElementEvent;
import org.apache.jcs.engine.control.event.behavior.IElementEventHandler;
import org.apache.jcs.engine.control.event.behavior.IElementEventQueue;

/**
 * An event queue is used to propagate ordered cache events to one and only one target listener.
 */
public class ElementEventQueue
    implements IElementEventQueue
{
    /** The logger */
    private final static Log log = LogFactory.getLog( ElementEventQueue.class );

    /** number of processors */
    private static int processorInstanceCount = 0;

    /** The cache (region) name. */
    private String cacheName;

    /** shutdown or not */
    private boolean destroyed = false;

    /** The worker thread. */
    private Thread t;

    /** Internal queue implementation */
    private Object queueLock = new Object();

    /** Dummy node */
    private Node head = new Node();

    /** tail of the doubly linked list */
    private Node tail = head;

    /**
     * Constructor for the ElementEventQueue object
     * @param cacheName
     */
    public ElementEventQueue( String cacheName )
    {

        this.cacheName = cacheName;

        t = new QProcessor();
        t.start();

        if ( log.isDebugEnabled() )
        {
            log.debug( "Constructed: " + this );
        }
    }

    /**
     * Event Q is emtpy.
     */
    public synchronized void destroy()
    {
        if ( !destroyed )
        {
            destroyed = true;

            // sychronize on queue so the thread will not wait forever,
            // and then interrupt the QueueProcessor

            synchronized ( queueLock )
            {
                t.interrupt();
            }

            t = null;

            log.info( "Element event queue destroyed: " + this );
        }
    }

    /**
     * @return the region name for the event queue
     */
    public String toString()
    {
        return "cacheName=" + cacheName;
    }

    /**
     * @return The destroyed value
     */
    public boolean isAlive()
    {
        return ( !destroyed );
    }

    /**
     * Adds an ElementEvent to be handled
     * @param hand The IElementEventHandler
     * @param event The IElementEventHandler IElementEvent event
     * @throws IOException
     */
    public void addElementEvent( IElementEventHandler hand, IElementEvent event )
        throws IOException
    {

        if ( log.isDebugEnabled() )
        {
            log.debug( "Adding Event Handler to QUEUE, !destroyed = " + !destroyed );
        }

        if ( !destroyed )
        {
            ElementEventRunner runner = new ElementEventRunner( hand, event );

            if ( log.isDebugEnabled() )
            {
                log.debug( "runner = " + runner );
            }

            put( runner );
        }
    }

    /**
     * Adds an event to the queue.
     * @param event
     */
    private void put( AbstractElementEventRunner event )
    {
        Node newNode = new Node();

        newNode.event = event;

        synchronized ( queueLock )
        {
            tail.next = newNode;
            tail = newNode;

            queueLock.notify();
        }
    }

    /**
     * Returns the next item on the queue, or waits if empty.
     * <p>
     * @return AbstractElementEventRunner
     * @throws InterruptedException
     */
    private AbstractElementEventRunner take()
        throws InterruptedException
    {
        synchronized ( queueLock )
        {
            // wait until there is something to read

            while ( head == tail )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( "Waiting for something to come into the Q" );
                }

                queueLock.wait();

                if ( log.isDebugEnabled() )
                {
                    log.debug( "Something came into the Q" );
                }
            }

            // we have the lock, and the list is not empty

            Node node = head.next;

            AbstractElementEventRunner value = node.event;

            if ( log.isDebugEnabled() )
            {
                log.debug( "head.event = " + head.event );
                log.debug( "node.event = " + node.event );
            }

            // Node becomes the new head (head is always empty)

            node.event = null;
            head = node;

            return value;
        }
    }

    // /////////////////////////// Inner classes /////////////////////////////

    /** A node in the queue. These are chained forming a singly linked list */
    private static class Node
    {
        /** The next node. */
        Node next = null;

        /** The event to run */
        ElementEventQueue.AbstractElementEventRunner event = null;
    }

    /**
     */
    private class QProcessor
        extends Thread
    {
        /**
         * Constructor for the QProcessor object
         */
        QProcessor()
        {
            super( "ElementEventQueue.QProcessor-" + ( ++processorInstanceCount ) );

            setDaemon( true );
        }

        /**
         * Main processing method for the QProcessor object
         */
        public void run()
        {
            AbstractElementEventRunner r = null;

            while ( !destroyed )
            {
                try
                {
                    r = take();

                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "r from take() = " + r );
                    }

                }
                catch ( InterruptedException e )
                {
                    // We were interrupted, so terminate gracefully.
                    this.destroy();
                }

                if ( !destroyed && r != null )
                {
                    r.run();
                }
            }

            log.info( "QProcessor exiting for " + ElementEventQueue.this );
        }
    }

    /**
     * Retries before declaring failure.
     */
    private abstract class AbstractElementEventRunner
        implements Runnable
    {
        /**
         * Main processing method for the AbstractElementEvent object
         */
        public void run()
        {
            IOException ex = null;

            try
            {
                ex = null;
                doRun();
                return;
                // happy and done.
            }
            catch ( IOException e )
            {
                ex = e;
            }

            // Too bad. The handler has problems.
            if ( ex != null )
            {
                log.warn( "Giving up element event handling " + ElementEventQueue.this, ex );

            }
            return;
        }

        /**
         * This will do the work or trigger the work to be done.
         * <p>
         * @exception IOException
         */
        protected abstract void doRun()
            throws IOException;
    }

    /**
     * ElementEventRunner.
     */
    private class ElementEventRunner
        extends AbstractElementEventRunner
    {
        /** the handler */
        private IElementEventHandler hand;

        /** event */
        private IElementEvent event;

        /**
         * Constructor for the PutEvent object.
         * <p>
         * @param hand
         * @param event
         * @exception IOException
         */
        ElementEventRunner( IElementEventHandler hand, IElementEvent event )
            throws IOException
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "Constructing " + this );
            }
            this.hand = hand;
            this.event = event;
        }

        /**
         * Tells the handler to handle the event.
         * <p>
         * @exception IOException
         */
        protected void doRun()
            throws IOException
        {
            hand.handleElementEvent( event );
        }
    }
}
