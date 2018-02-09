package org.codehaus.stax.test.stream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax.test.BaseStaxTest;

/**
 * Base class for all StaxTest unit tests that test basic non-validating
 * stream (cursor) API functionality.
 *
 * @author Tatu Saloranta
 */
public class BaseStreamTest
    extends BaseStaxTest
{
    /**
     * Switch that can be turned on to verify to display ALL exact Exceptions
     * thrown when Exceptions are expected. This is sometimes necessary
     * when debugging, since it's impossible to automatically verify
     * that Exception is exactly the right one, since there is no
     * strict Exception type hierarchy for StAX problems.
     *<p>
     * Note: Not made 'final static', so that compiler won't inline
     * it. Makes possible to do partial re-compilations.
     * Note: Since it's only used as the default value, sub-classes
     *  can separately turn it off as necessary
     */
    //protected static boolean DEF_PRINT_EXP_EXCEPTION = true;
    protected static boolean DEF_PRINT_EXP_EXCEPTION = false;

    protected boolean PRINT_EXP_EXCEPTION = DEF_PRINT_EXP_EXCEPTION;

    protected BaseStreamTest() { super(); }

    protected BaseStreamTest(String name) {
        super(name);
    }

    /*
    //////////////////////////////////////////////////
    // "Special" accessors
    //////////////////////////////////////////////////
     */

    /*
    //////////////////////////////////////////////////
    // Higher-level test methods
    //////////////////////////////////////////////////
     */

    /**
     * Method that will iterate through contents of an XML document
     * using specified stream reader; will also access some of data
     * to make sure reader reads most of lazy-loadable data.
     * Method is usually called to try to get an exception for invalid
     * content.
     *
     * @return Dummy value calculated on contents; used to make sure
     *   no dead code is eliminated
     */
    protected int streamThrough(XMLStreamReader sr)
        throws XMLStreamException
    {
        int result = 0;

        while (sr.hasNext()) {
            int type = sr.next();
            result += type;
            if (sr.hasText()) {
                /* will also do basic verification for text content, to 
                 * see that all text accessor methods return same content
                 */
                result += getAndVerifyText(sr).hashCode();
            }
            if (sr.hasName()) {
                result += sr.getName().hashCode();
            }
        }

        return result;
    }

    protected int streamThroughFailing(XMLInputFactory f, String contents,
                                       String msg)
        throws XMLStreamException
    {
        int result = 0;
        try {
            XMLStreamReader sr = constructStreamReader(f, contents);
            result = streamThrough(sr);
        } catch (XMLStreamException ex) { // good
            if (PRINT_EXP_EXCEPTION) {
                System.out.println("Expected failure: '"+ex.getMessage()+"' "
                                   +"(matching message: '"+msg+"')");
            }
            return 0;
        } catch (RuntimeException ex2) { // ok
            if (PRINT_EXP_EXCEPTION) {
                System.out.println("Expected failure: '"+ex2.getMessage()+"' "
                                   +"(matching message: '"+msg+"')");
            }
            return 0;
        } catch (Throwable t) { // not so good
            fail("Expected an XMLStreamException or RuntimeException for "+msg
                 +", got: "+t);
        }

        fail("Expected an exception for "+msg);
        return result; // never gets here
    }

    protected int streamThroughFailing(XMLStreamReader sr, String msg)
        throws XMLStreamException
    {
        int result = 0;
        try {
            result = streamThrough(sr);
        } catch (XMLStreamException ex) { // good
            if (PRINT_EXP_EXCEPTION) {
                System.out.println("Expected failure: '"+ex.getMessage()+"' "
                                   +"(matching message: '"+msg+"')");
            }
            return 0;
        } catch (RuntimeException ex2) { // ok
            if (PRINT_EXP_EXCEPTION) {
                System.out.println("Expected failure: '"+ex2.getMessage()+"' "
                                   +"(matching message: '"+msg+"')");
            }
            return 0;
        } catch (Throwable t) { // not so good
            fail("Expected an XMLStreamException or RuntimeException for "+msg
                 +", got: "+t);
        }

        fail("Expected an exception for "+msg);
        return result; // never gets here
    }
}
