package org.apache.jcs.utils.props;

/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.InputStream;
import java.util.Properties;

/**
 * I modified this class to work with .ccf files in particular. I also removed
 * the resource bundle functionality.
 * 
 * A simple class for loading java.util.Properties backed by .ccf files deployed
 * as classpath resources. See individual methods for details.
 * <p>
 * The original source is from:
 * 
 * @author (C) <a
 *         href="http://www.javaworld.com/columns/jw-qna-index.shtml">Vlad
 *         Roubtsov </a>, 2003
 *  
 */
public abstract class PropertyLoader
{

    private static final boolean THROW_ON_LOAD_FAILURE = true;

    private static final String SUFFIX = ".ccf";

    /**
     * Looks up a resource named 'name' in the classpath. The resource must map
     * to a file with .ccf extention. The name is assumed to be absolute and can
     * use either "/" or "." for package segment separation with an optional
     * leading "/" and optional ".ccf" suffix.
     * <p>
     * The suffix ".ccf" will be appended if it is not set.
     * 
     * Thus, the following names refer to the same resource:
     * 
     * <pre>
     * 
     *  
     *   
     *    
     *     some.pkg.Resource
     *     some.pkg.Resource.ccf
     *     some/pkg/Resource
     *     some/pkg/Resource.ccf
     *     /some/pkg/Resource
     *     /some/pkg/Resource.ccf
     *     
     *    
     *   
     *  
     * </pre>
     * 
     * @param name
     *            classpath resource name [may not be null]
     * @param loader
     *            classloader through which to load the resource [null is
     *            equivalent to the application loader]
     * 
     * @return resource converted to java.util.properties [may be null if the
     *         resource was not found and THROW_ON_LOAD_FAILURE is false]
     * @throws IllegalArgumentException
     *             if the resource was not found and THROW_ON_LOAD_FAILURE is
     *             true
     */
    public static Properties loadProperties( String name, ClassLoader loader )
    {
        if ( name == null )
            throw new IllegalArgumentException( "null input: name" );

        if ( name.startsWith( "/" ) )
        {
            name = name.substring( 1 );
        }

        if (name.endsWith (SUFFIX))
        {            
            name = name.substring (0, name.length () - SUFFIX.length ());
        }
        
        Properties result = null;

        InputStream in = null;
        try
        {
            if ( loader == null )
            {                
                loader = ClassLoader.getSystemClassLoader();
            }

            name = name.replace( '.', '/' );

            if ( !name.endsWith( SUFFIX ) )
            {
                name = name.concat( SUFFIX );
            }

            // returns null on lookup failures:
            in = loader.getResourceAsStream( name );
            if ( in != null )
            {
                result = new Properties();
                result.load( in ); // can throw IOException
            }
        }
        catch ( Exception e )
        {
            result = null;
        }
        finally
        {
            if ( in != null )
                try
                {
                    in.close();
                }
                catch ( Throwable ignore )
                {
                }
        }

        if ( THROW_ON_LOAD_FAILURE && ( result == null ) )
        {
            throw new IllegalArgumentException( "could not load [" + name + "]" + " as " + "a classloader resource" );
        }

        return result;
    }

    /**
     * A convenience overload of {@link #loadProperties(String, ClassLoader)}
     * that uses the current thread's context classloader. A better strategy
     * would be to use techniques shown in
     * http://www.javaworld.com/javaworld/javaqa/2003-06/01-qa-0606-load.html
     */
    public static Properties loadProperties( final String name )
    {
        return loadProperties( name, Thread.currentThread().getContextClassLoader() );
    }

    private PropertyLoader()
    {
    } // this class is not extentible

}