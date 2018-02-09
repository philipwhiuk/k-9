package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of the XML attributes, both
 * in namespace aware and non-namespace modes, including ensuring
 * that values are properly normalized with regards to white space.
 */
public class TestAttributeRead
    extends BaseStreamTest
{
    final String VALID_XML1
        = "<root a='r&amp;b' a:b=\"&quot;\" xmlns:a='url' />";

    final String VALID_XML2
        = "<root a:b=\"&quot;\" xmlns:a='url' />";

    /**
     * Test to make sure that quotes can be used in attribute values
     * via entity expansion
     */
    final String VALID_ATTRS_WITH_QUOTES
        = "<!DOCTYPE tree [\n"
        + "<!ENTITY val1 '\"quoted\"'>\n"
        + "<!ENTITY val2 \"'quoted too'\"> ]>\n"
        + "<tree attr='&val1;' attr2=\"&val1;\" "
        +" attr3='&val2;' attr4=\"&val2;\" />";

    public void testValidNsAttrsByName()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        assertEquals("r&b", sr.getAttributeValue(null, "a"));
        assertEquals("\"", sr.getAttributeValue("url", "b"));

        // Shoulnd't allow using prefix instead of URI
        String val = sr.getAttributeValue("a", "b");
        assertNull("Should get null, not '"+val+"'", val);
        val = sr.getAttributeValue("", "b");
        assertNull("Should get null, not '"+val+"'", val);

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testValidNsAttrsByIndex()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        /* Note: we can not assume on stream reader returning attributes
         * in document order... most will do that, but it's not a
         * strict StAX requirement.
         */

        String ln1 = sr.getAttributeLocalName(0);

        int index1 = 0;
        int index2 = 1;

        if (ln1.equals("a")) {
            ;
        } else if (ln1.equals("b")) {
            index1 = 1;
            index2 = 0;
        } else {
            fail("Unexpected local name for attribute #0; expected either 'a' or 'b'; got '"+ln1+"'.");
        }
        
        assertEquals("a", sr.getAttributeLocalName(index1));
        assertEquals("b", sr.getAttributeLocalName(index2));
        
        assertEquals("r&b", sr.getAttributeValue(index1));
        assertEquals("\"", sr.getAttributeValue(index2));
        String prefix = sr.getAttributePrefix(index1);
        if (prefix != null) {
            fail("Expected null as prefix for attribute 'a', got '"+prefix+"'");
        }
        assertEquals("a", sr.getAttributePrefix(index2));
        String ns = sr.getAttributeNamespace(index1);
        assertNull("Unbound attribute should return null for XMLStreamReader.sr.getAttributeNamespace(int): got '"+ns+"'", ns);
        assertEquals("url", sr.getAttributeNamespace(index2));
    }

    public void testValidNsAttrNsInfo()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader
            ("<root a='xyz' xmlns:b='http://foo'><leaf b:attr='1' /></root>",
             true);

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());
        assertNull(sr.getAttributePrefix(0));
        assertNull(sr.getAttributeNamespace(0));
        assertEquals("xyz", sr.getAttributeValue(0));

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("b", sr.getAttributePrefix(0));
        assertEquals("http://foo", sr.getAttributeNamespace(0));
        assertEquals("1", sr.getAttributeValue(0));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testValidNonNsAttrs()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, false);

        // Does the impl support non-ns mode?
        if (sr == null) { // nope!
            return;
        }

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(3, sr.getAttributeCount());

        assertEquals("r&b", sr.getAttributeValue(null, "a"));
        assertEquals("\"", sr.getAttributeValue(null, "a:b"));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testValidNonNsAttrsByIndex()
        throws XMLStreamException
    {
        // = "<root a:b=\"&quot;\" xmlns:a='url' />";
        XMLStreamReader sr = getReader(VALID_XML2, false);

        // Does the impl support non-ns mode?
        if (sr == null) { // nope!
            return;
        }

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        /* Note: we can not assume on stream reader returning attributes
         * in document order... most will do that, but it's not a
         * strict StAX requirement.
         */

        String ln1 = sr.getAttributeLocalName(0);

        int index1 = 0;
        int index2 = 1;

        if (ln1.equals("a:b")) {
            ;
        } else if (ln1.equals("xmlns:a")) {
            index1 = 1;
            index2 = 0;
        } else {
            fail("Unexpected local name for attribute #0; expected either 'a:b' or 'xmlns:a'; got '"+ln1+"'.");
        }
        
        assertEquals("a:b", sr.getAttributeLocalName(index1));
        assertEquals("xmlns:a", sr.getAttributeLocalName(index2));
        
        assertEquals("\"", sr.getAttributeValue(index1));
        assertEquals("url", sr.getAttributeValue(index2));
        assertNull(sr.getAttributePrefix(index1));
        assertNull(sr.getAttributePrefix(index2));

        assertNull(sr.getAttributeNamespace(index1));
        assertNull(sr.getAttributeNamespace(index2));
    }

    public void testQuotesViaEntities()
        throws XMLStreamException
    {
        XMLInputFactory ifact = getNewInputFactory();
        setNamespaceAware(ifact, false); // shouldn't matter
        // These are needed to get entities read and expanded:
        setSupportDTD(ifact, true); 
        setReplaceEntities(ifact, true); 

        XMLStreamReader sr = constructStreamReader(ifact,
                                                   VALID_ATTRS_WITH_QUOTES);
        // Shouldn't get exceptions...

        try {
            streamThrough(sr);
        } catch (XMLStreamException ex) {
            fail("Failed to parse attributes with quotes expanded from entities: "+ex);
        }
    }

    public void testInvalidAttrNames()
        throws XMLStreamException
    {
	// First NS-aware, then non-NS:
	streamThroughFailing(getReader("<tree .attr='value' />", true),
			     "invalid attribute name; can not start with '.'");
	streamThroughFailing(getReader("<tree .attr='value' />", false),
			     "invalid attribute name; can not start with '.'");

	streamThroughFailing(getReader("<tree attr?='value' />", false),
			     "invalid attribute name can not contain '?'");
	streamThroughFailing(getReader("<tree attr?='value' />", true),
			     "invalid attribute name can not contain '?'");
    }

    public void testInvalidAttrValue()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean ns = (i > 0);
            // Invalid, '<' not allowed in attribute value
            String XML = "<root a='<' />";
            streamThroughFailing(getReader(XML, ns),
                                 "unquoted '<' in attribute value");
            
            XML = "<root a />";
            streamThroughFailing(getReader(XML, ns),
                                 "missing value for attribute");
        }
    }

    /**
     * This tests that spaces are actually needed between attributes...
     */
    public void testInvalidAttrSpaces()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean ns = (i > 0);
            String XML = "<root a='b'b='a' />";
            streamThroughFailing(getReader(XML, ns),
                                 "missing space between attributes");
            XML = "<root a=\"b\"b=\"a\" />";
            streamThroughFailing(getReader(XML, ns),
                                 "missing space between attributes");
        }
    }

    public void testInvalidNsAttrDup()
        throws XMLStreamException
    {
        // Invalid; straight duplicate attrs:
        String XML = "<root xmlns:a='xxx' a:attr='1' a:attr='2' />";
        streamThroughFailing(getReader(XML, true),
                             "duplicate attributes");
        // Invalid; sneakier duplicate attrs:
        XML = "<root xmlns:a='xxx' xmlns:b='xxx' a:attr='1' b:attr='2' />";
        streamThroughFailing(getReader(XML, true),
                             "duplicate attributes (same URI, different prefix)");
    }
    
    public void testInvalidNonNsAttrDup()
        throws XMLStreamException
    {
        // Invalid; duplicate attrs even without namespaces
        String XML = "<root xmlns:a='xxx' a:attr='1' a:attr='2' />";
        streamThroughFailing(getReader(XML, false),
                             "duplicate attributes");
        
        // Valid when namespaces not enabled:
        XML = "<root xmlns:a='xxx' xmlns:b='xxx' a:attr='1' b:attr='2' />";
        try {
            XMLStreamReader sr = getReader(XML, false);

            // Does the impl support non-ns mode?
            if (sr == null) { // nope! shouldn't test...
                return;
            }
            streamThrough(sr);
        } catch (Exception e) {
            fail("Didn't expect an exception when namespace support not enabled: "+e);
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
    private XMLStreamReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        if (!setNamespaceAware(f, nsAware)) {
            return null;
        }

        setCoalescing(f, true); // shouldn't matter
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
