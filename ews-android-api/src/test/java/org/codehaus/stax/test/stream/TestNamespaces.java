package org.codehaus.stax.test.stream;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.*;
import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of the namespace declarations,
 * both in namespace aware and non-namespace modes.
 */
public class TestNamespaces
    extends BaseStreamTest
{
    final String VALID_NS_XML
        = "<root xmlns:a='myurl' attr1='value' a:attr1=''>"
        +"<a:branch xmlns='someurl' xmlns:a='whatever' attr='value'>"
        +"<leaf a='xxx' a:a='yyy' />"
        +"</a:branch>"
        +"</root>";

    public TestNamespaces(String name) {
        super(name);
    }

    public void testValidNs()
        throws XMLStreamException
    {
        XMLStreamReader sr = getNsReader(VALID_NS_XML, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());
        // element properties:
        assertNull(sr.getPrefix());
        assertEquals("root", sr.getLocalName());
        String uri = sr.getNamespaceURI();
        if (uri != null) {
            fail("Undeclared (default) namespace should have null URI; had \""+uri+"\" (len "+uri.length()+")");
        }
        // ns/attr properties:
        assertEquals("value", sr.getAttributeValue(null, "attr1"));
        assertEquals("", sr.getAttributeValue("myurl", "attr1"));
        try {
            String str = sr.getAttributeValue(-1);
            fail("Expected an exception when trying to access attribute #-1.");
        } catch (Exception e) { }
        try {
            String str = sr.getAttributeValue(2);
            fail("Expected an exception when trying to access attribute #2 (only 2 attibutes, #0 and #1.");
        } catch (Exception e) { }
        // Shouldn't be able to use prefix, just URI:
        assertNull(sr.getAttributeValue("xmlns", "a"));
        assertEquals("myurl", sr.getNamespaceURI("a"));
        assertNull(sr.getNamespaceURI("myurl"));
        assertNull(sr.getNamespaceURI(""));
        assertNull(sr.getNamespaceURI("nosuchurl"));

        NamespaceContext nc = sr.getNamespaceContext();
        assertNotNull(nc);
        assertEquals(XMLConstants.XML_NS_URI, nc.getNamespaceURI(XMLConstants.XML_NS_PREFIX));
        assertEquals(XMLConstants.XML_NS_PREFIX, nc.getPrefix(XMLConstants.XML_NS_URI));
        assertEquals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, nc.getNamespaceURI(XMLConstants.XMLNS_ATTRIBUTE));
        assertEquals(XMLConstants.XMLNS_ATTRIBUTE, nc.getPrefix(XMLConstants.XMLNS_ATTRIBUTE_NS_URI));

        assertEquals("myurl", nc.getNamespaceURI("a"));
        assertEquals("a", nc.getPrefix("myurl"));
        Iterator it = nc.getPrefixes("foobar");
        // Hmmmh. Can it be null or not? For now let's allow null too
        if (it == null) {
            ;
        } else {
            assertFalse(it.hasNext());
        }
        it = nc.getPrefixes("myurl");
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("a", (String) it.next());
        assertFalse(it.hasNext());

        // // Ok, then the second element:

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(2, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("a", sr.getPrefix());
        assertEquals("branch", sr.getLocalName());

        assertEquals("whatever", sr.getNamespaceURI());
        assertEquals("value", sr.getAttributeValue(null, "attr"));
        assertEquals("value", sr.getAttributeValue(0));

        assertEquals("someurl", sr.getNamespaceURI(""));

        // // And finally the third

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());
        assertNull(sr.getPrefix());
        assertEquals("leaf", sr.getLocalName());
        assertEquals("yyy", sr.getAttributeValue("whatever", "a"));
        assertEquals("xxx", sr.getAttributeValue(null, "a"));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertNull(sr.getPrefix());
        assertEquals("leaf", sr.getLocalName());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(2, sr.getNamespaceCount());
        assertEquals("a", sr.getPrefix());
        assertEquals("branch", sr.getLocalName());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertNull(sr.getPrefix());
        assertEquals("root", sr.getLocalName());

        assertEquals(END_DOCUMENT, sr.next());
        assertFalse(sr.hasNext());
    }

    final String VALID_NS_XML2
        ="<?xml version='1.0' ?>"
        +"<root xmlns:a='myurl' xmlns=\"http://foo\">text"
        +"<empty attr='&amp;'/><a:empty /></root>";

    /**
     * Another unit test that checks that valid namespace declarations
     * are handled properly.
     */
    public void testMultipleValidNs()
        throws XMLStreamException
    {
        XMLStreamReader sr = getNsReader(VALID_NS_XML2, true);
        assertEquals(START_ELEMENT, sr.next());

        // Let's thoroughly check the root elem
        assertEquals(2, sr.getNamespaceCount());
        assertEquals(0, sr.getAttributeCount());
        assertNull(sr.getPrefix());
        assertEquals("root", sr.getLocalName());
        assertEquals("http://foo", sr.getNamespaceURI());
        assertEquals("myurl", sr.getNamespaceURI("a"));

        // first empty elem
        while (sr.next() == CHARACTERS) { }
        assertTokenType(START_ELEMENT, sr.getEventType());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());
        assertNull(sr.getPrefix());
        assertEquals("empty", sr.getLocalName());
        assertEquals("http://foo", sr.getNamespaceURI());
        assertEquals("myurl", sr.getNamespaceURI("a"));
        assertNull(sr.getAttributeNamespace(0));
        assertNull(sr.getAttributePrefix(0));
        assertEquals("&", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        // second empty elem
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(0, sr.getAttributeCount());
        assertEquals("empty", sr.getLocalName());
        assertEquals("a", sr.getPrefix());
        assertEquals("myurl", sr.getNamespaceURI());
        assertEquals("myurl", sr.getNamespaceURI("a"));
        assertEquals("http://foo", sr.getNamespaceURI(""));
        assertTokenType(END_ELEMENT, sr.next());

        // And closing 'root'
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals("http://foo", sr.getNamespaceURI());

        assertTokenType(END_DOCUMENT, sr.next());
    }

    /**
     * Test proper handling of valid xml content in non-namespace aware mode.
     * Since not all implementations (like the ref. impl.) support non-ns
     * aware mode, this unit test is skipped if not applicable.
     */
    public void testValidNonNs()
        throws XMLStreamException
    {
        XMLStreamReader sr = getNsReader(VALID_NS_XML, false);
        if (sr == null) {
            reportNADueToNS("testValidNonNs");
            return;
        }

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(3, sr.getAttributeCount());
        // element properties:
        assertNull(sr.getPrefix());
        assertEquals("root", sr.getLocalName());

        // Hmmmh. Should we expect null here or not?
        assertEquals(null, sr.getNamespaceURI());
        // ns/attr properties:

        assertEquals("value", sr.getAttributeValue(null, "attr1"));
        assertEquals(null, sr.getAttributeValue(null, "foobar"));

        try {
            String str = sr.getAttributeValue(-1);
            fail("Expected an exception when trying to access attribute #-1.");
        } catch (Exception e) { }
        try {
            String str = sr.getAttributeValue(3);
            fail("Expected an exception when trying to access attribute #3 (only 3 attibutes, #0 and #1.");
        } catch (Exception e) { }


        /* ... not sure if how namespace access should work, ie. is it ok
         * to throw an exception, return null or what
         */

        // // Ok, then the second element:

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(3, sr.getAttributeCount());
        assertNull(sr.getPrefix());
        assertEquals("a:branch", sr.getLocalName());

        // // And finally the third

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());
        assertNull(sr.getPrefix());
        assertEquals("leaf", sr.getLocalName());
        assertEquals("xxx", sr.getAttributeValue(null, "a"));
        assertEquals("yyy", sr.getAttributeValue(null, "a:a"));

        // // And then the end elements

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertNull(sr.getPrefix());
        assertEquals("leaf", sr.getLocalName());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertNull(sr.getPrefix());
        assertEquals("a:branch", sr.getLocalName());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertNull(sr.getPrefix());
        assertEquals("root", sr.getLocalName());

        assertEquals(END_DOCUMENT, sr.next());
        assertFalse(sr.hasNext());
    }

    public void testInvalidNs()
        throws XMLStreamException
    {
        testPotentiallyInvalid(true, "testInvalidNs");
    }

    public void testInvalidNonNs()
        throws XMLStreamException
    {
        // Some things are ok, some not, when namespace support is not enabled:
        testPotentiallyInvalid(false, "testInvalidNonNs");
    }

    public void testInvalidStandardBindings()
        throws XMLStreamException
    {
        doTestXmlBinding(true, "testInvalidStandardBindings");
        doTestXmlnsBinding(true, "testInvalidStandardBindings");
    }

    public void testInvalidStandardBindingsNonNs()
        throws XMLStreamException
    {
        doTestXmlBinding(false, "testInvalidStandardBindingsNonNs");
        doTestXmlnsBinding(false, "testInvalidStandardBindingsNonNs");
    }

    public void testDefaultNs()
        throws XMLStreamException
    {
        String XML = "<root xmlns='url' />";

        XMLStreamReader sr = getNsReader(XML, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertNull(sr.getPrefix());
        assertEquals("url", sr.getNamespaceURI());
        NamespaceContext ctxt = sr.getNamespaceContext();
        assertEquals(1, sr.getNamespaceCount());
        assertEquals("url", sr.getNamespaceURI(0));
        assertNull(sr.getNamespacePrefix(0));

        assertEquals("url", ctxt.getNamespaceURI(""));
        assertEquals("", ctxt.getPrefix("url"));
        assertNull(ctxt.getNamespaceURI("b"));
        assertNull(ctxt.getPrefix("ns:b"));


        XML = "<root xmlns:a='url' />";

        sr = getNsReader(XML, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertNull(sr.getPrefix());
        assertNull(sr.getNamespaceURI());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals("url", sr.getNamespaceURI(0));
        assertEquals("a", sr.getNamespacePrefix(0));
    }

    /**
     * Test case that verifies that namespaces properly nest, and
     * inner definitions (ns in child element) can mask outer
     * definitions (ns in parent element)
     */
    public void testMaskingNs()
        throws XMLStreamException
    {
        final String XML =
            "<a:root xmlns:a='ns:a'><a:child xmlns:a='ns:b' /></a:root>";

        XMLStreamReader sr = getNsReader(XML, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals("a", sr.getPrefix());
        assertEquals("ns:a", sr.getNamespaceURI());
        NamespaceContext ctxt = sr.getNamespaceContext();
        assertEquals("ns:a", ctxt.getNamespaceURI("a"));
        assertNull(ctxt.getNamespaceURI("b"));
        assertEquals("a", ctxt.getPrefix("ns:a"));
        assertNull(ctxt.getPrefix("ns:b"));

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("child", sr.getLocalName());
        assertEquals("a", sr.getPrefix());
        assertEquals("ns:b", sr.getNamespaceURI());

        ctxt = sr.getNamespaceContext();
        assertEquals("ns:b", ctxt.getNamespaceURI("a"));
        assertEquals("a", ctxt.getPrefix("ns:b"));
        assertNull(ctxt.getNamespaceURI("b"));

        // This is testing of actual masking, using NamespaceContext
        {
            // Previous binding should be masked by now!
            String prefix = ctxt.getPrefix("ns:a");
            if (prefix != null) {
                fail("Failed: second declaration for prefix 'a' should have masked previous one; and there should not be a prefix for 'ns:a'. Instead, prefix '"+prefix+"' was considered to (still) be bound");
            }
        }
    }

    /**
     * Unit test that verifies that the default namespace masking works
     * as expected.
     */
    public void testMaskingDefaultNs()
        throws XMLStreamException
    {
        final String XML =
            "<root xmlns='someurl'>"
            +"<branch xmlns=''><leaf /><leaf xmlns='anotherurl' />"
            +"</branch></root>"
            ;

        XMLStreamReader sr = getNsReader(XML, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertNoPrefix(sr);
        assertEquals("someurl", sr.getNamespaceURI());
        assertEquals(1, sr.getNamespaceCount());

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertNoPrefix(sr);
        assertNoNsURI(sr);
        assertEquals(1, sr.getNamespaceCount());

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertNoPrefix(sr);
        assertNoNsURI(sr);
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(END_ELEMENT, sr.next()); // leaf
        assertEquals(0, sr.getNamespaceCount());

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertNoPrefix(sr);
        assertEquals("anotherurl", sr.getNamespaceURI());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(END_ELEMENT, sr.next()); // leaf
        assertEquals(1, sr.getNamespaceCount());

        assertEquals(END_ELEMENT, sr.next()); // branch
        assertEquals(1, sr.getNamespaceCount());

        assertEquals(END_ELEMENT, sr.next()); // root
        assertEquals(1, sr.getNamespaceCount());
    }

    /**
     * Unit test that verifies that the namespace with prefix 'xml' is
     * always predefined without further work.
     */
    public void testPredefinedXmlNs()
        throws XMLStreamException
    {
        final String XML = "<root xml:lang='fi'><xml:a /></root>";

        XMLStreamReader sr = getNsReader(XML, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("xml", sr.getAttributePrefix(0));
        assertEquals("lang", sr.getAttributeLocalName(0));
        assertEquals(XMLConstants.XML_NS_URI, sr.getAttributeNamespace(0));
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("xml", sr.getPrefix());
        assertEquals("a", sr.getLocalName());
        assertEquals(XMLConstants.XML_NS_URI, sr.getNamespaceURI());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_ELEMENT, sr.next());
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    private void testPotentiallyInvalid(boolean nsAware, String method)
        throws XMLStreamException
    {
        // First, check that undeclared namespace prefixes are not kosher
        try {
            XMLStreamReader sr = getNsReader("<ns1:elem />", nsAware);
            if (sr == null) {
                reportNADueToNS(method);
                return;
            }
            
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that uses undeclared namespace prefix.");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for undeclared namespace when namespaces support not enabled: "+e);
            }
        }

        // Plus, can't redeclare default namespace
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns='aaa' xmlns='bbb' />",
                                             nsAware);
            streamThrough(sr);
            fail("Was expecting an exception for content that has duplicate declaration of the default namespace.");
        } catch (Exception e) {
            ; // both should get here
        }

        // Nor other prefixes
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns:a='aaa' xmlns:a='bbb' />",
                                             nsAware);
            streamThrough(sr);
            fail("Was expecting an exception for content that has duplicate declaration for a prefix.");
        } catch (Exception e) {
            ; // both should get here
        }

        /* And then, two attribute names may be equivalent if prefixes
         * point to same URI; but only in namespace-aware mode
         */
        try {
            XMLStreamReader sr = getNsReader
                ("<elem xmlns:a=\"aaa\" xmlns:b='aaa' a:attr1='1' b:attr1=\"2\" />",
                 nsAware);
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that has duplicate attribute (even though prefixes differe, as they point to the same URI)");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was NOT expecting an exception since in non-namespace mode attributes 'a:attr1' and 'b:attr1' are not equivalent: "+e);
            }
        }
    }

    private void doTestXmlBinding(boolean nsAware, String method)
        throws XMLStreamException
    {
        // And 'xml' can only be bound to its correct URI
        { // this should be fine
            XMLStreamReader sr = getNsReader("<elem xmlns:xml='"+XMLConstants.XML_NS_URI+"' />", nsAware);
            if (sr == null) {
                reportNADueToNS(method);
                return;
            }
            streamThrough(sr);
        }

        // But not to anything else:
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns:xml='xxx' />", nsAware);
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that tries to redeclare 'xml' to different URI.");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for redeclaration of 'xml' when namespace support not enabled: "+e);
            }
        }

        // Also, nothing else can bind to that URI, neither explicit prefix
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns:foo='"+XMLConstants.XML_NS_URI+"' />", nsAware);
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that tries to bind prefix other than 'xml' to URI '"+XMLConstants.XML_NS_URI+"'");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for binding 'xml' URI");
            }
        }

        // Nor default namespace
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns='"+XMLConstants.XML_NS_URI+"' />", nsAware);
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that tries to bind the default namespace to 'xml' URI '"+XMLConstants.XML_NS_URI+"'");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for binding default namespace to 'xml' URI");
            }
        }
    }

    private void doTestXmlnsBinding(boolean nsAware, String method)
        throws XMLStreamException
    {
        // Illegal to try to (re)declare 'xmlns' in any way
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns:xmlns='yyy' />", nsAware);
            if (sr == null) {
                reportNADueToNS(method);
                return;
            }
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that tries to redeclare 'xml' or 'xmlns' to different URI.");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for redeclaration of 'xmlns' when namespace support not enabled: "+e);
            }
        }

        // Also, nothing else can bind to that URI, neither explicit prefix
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns:foo='"+XMLConstants.XMLNS_ATTRIBUTE_NS_URI+"' />", nsAware);
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that tries to bind prefix other than 'xml' to URI '"+XMLConstants.XMLNS_ATTRIBUTE_NS_URI+"'");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for binding 'xml' URI");
            }
        }

        // Nor default namespace
        try {
            XMLStreamReader sr = getNsReader("<elem xmlns='"+XMLConstants.XMLNS_ATTRIBUTE_NS_URI+"' />", nsAware);
            streamThrough(sr);
            if (nsAware) {
                fail("Was expecting an exception for content that tries to bind the default namespace to 'xml' URI '"+XMLConstants.XMLNS_ATTRIBUTE_NS_URI+"'");
            }
        } catch (Exception e) {
            if (!nsAware) {
                fail("Was not expecting an exception for binding default namespace to 'xml' URI");
            }
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    /**
     * @return Stream reader constructed if initialization succeeded (all
     *   setting supported by the impl); null if some settings (namespace
     *   awareness) not supported.
     */
    private XMLStreamReader getNsReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        if (!setNamespaceAware(f, nsAware)) {
            return null;
        }
        setCoalescing(f, true);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Local test driver, for quick access to individual cases...
    ////////////////////////////////////////////////////////////
     */

    public static void main(String[] args)
        throws Exception
    {
        new TestNamespaces("TestNamespaces").testValidNs();
    }
}
