package org.codehaus.stax.test.wstream;

import java.io.*;

import javax.xml.stream.*;

/**
 * Set of unit tests for verifying operation of {@link XMLStreamWriter}
 * in "repairing" mode.
 *
 * @author Tatu Saloranta
 */
public class TestRepairingWriter
    extends BaseWriterTest
{
    /**
     * Test similar to the one in {@link TestSimpleWriter}.
     */
    public void testElements()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getRepairingWriter(strw);
        final String URL_P1 = "http://p1.org";
        final String URL_P2 = "http://ns.p2.net/yeehaw.html";
        final String URL_DEF = "urn:default";

        final String TEXT = "  some text\n";

        w.writeStartDocument();

        /* Calling setPrefix() should be optional; but if we call it,
         * exceptation is that it does properly cause URL to be bound.
         */
        w.setPrefix("p1", URL_P1);
        w.writeStartElement(URL_P1, "test");

        w.writeStartElement("p2", "branch", URL_P2);

        // And then a dynamically created prefix...
        w.writeStartElement(URL_DEF, "leaf");

        w.writeCharacters(TEXT);

        w.writeEndElement(); // first leaf

        w.writeEmptyElement(URL_P1, "leaf"); // second leaf

        w.writeStartElement("", "third"); // may need dynamic NS too
        w.writeEndElement();

        w.writeEndElement(); // branch
        w.writeEndElement(); // root elem
        w.writeEndDocument();
        w.close();

        // And then let's parse and verify it all:
//System.err.println("Doc -> '"+strw.toString()+"'");
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType(), sr);

        // root element
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        // ??? is writer obligated to honor the prefix suggestion
        assertEquals(URL_P1, sr.getNamespaceURI());
        /* note: can not really verify number of namespace bindings, since
         * writer should be in charge... and it may output extra bindings,
         * too (and use default ns or explicit ones etc)
         */
        
        // first branch:
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("branch", sr.getLocalName());
        assertEquals(URL_P2, sr.getNamespaceURI());

        // first leaf
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        assertTokenType(CHARACTERS, sr.next(), sr);
        assertEquals(TEXT, getAllText(sr));
        // not: getAllText ^^^ moves cursor!

        assertTokenType(END_ELEMENT, sr.getEventType(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // another leaf:
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_P1, sr.getNamespaceURI());

        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_P1, sr.getNamespaceURI());

        // "third"
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("third", sr.getLocalName());
        assertNoNsURI(sr);
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("third", sr.getLocalName());
        assertNoNsURI(sr);

        // (close) branch
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("branch", sr.getLocalName());
        assertEquals(URL_P2, sr.getNamespaceURI());

        // closing root element
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertEquals(URL_P1, sr.getNamespaceURI());

        assertTokenType(END_DOCUMENT, sr.next(), sr);
    }

    public void testAttributeSimple()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getRepairingWriter(strw);
        final String URL_P1 = "http://p1.org";
        final String ATTR_VALUE = "'value'&\"another\"";

        w.writeStartDocument();
        w.writeStartElement("", "test");
        w.writeAttribute(URL_P1, "attr", ATTR_VALUE);
        w.writeEndElement();
        w.writeEndDocument();
        w.close();

//System.err.println("testAttributeSimple: doc = '"+strw+"'");

        // And then let's parse and verify it all:
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType(), sr);

        // root element
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertNoNsURI(sr);

        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals(URL_P1, sr.getAttributeNamespace(0));
        assertEquals(ATTR_VALUE, sr.getAttributeValue(0));

        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertNoNsURI(sr);

        assertTokenType(END_DOCUMENT, sr.next(), sr);
    }

    public void testAttributes()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getRepairingWriter(strw);
        final String URL_P1 = "http://p1.org";
        final String URL_DEF = "urn:default";
        final String ATTR_VALUE = "'value\"";
        final String ATTR_VALUE2 = "<tag>";

        w.writeStartDocument();

        /* Calling this method should be optional; but if we call it,
         * exceptation is that it does properly bind the prefix and URL
         * as the 'preferred' combination. In this case we'll just try
         * to make URL bound as the default namespace
         */
        w.setDefaultNamespace(URL_DEF);
        w.writeStartElement(URL_DEF, "test");

        /* And let's further make element and attribute(s) belong to that
         * same namespace
         */
        w.writeStartElement("", "leaf", URL_DEF);
        w.writeAttribute(URL_DEF, "attr", ATTR_VALUE);
        w.writeEndElement();

        w.writeEmptyElement("", "leaf"); // in empty/no namespace!

        w.writeStartElement(URL_DEF, "leaf");
        w.writeAttribute("", "attr2", ATTR_VALUE2); // in empty/no namespace
        w.writeEndElement();

        w.writeEndElement(); // root elem
        w.writeEndDocument();
        w.close();

        // And then let's parse and verify it all:
//System.err.println("testAttributes: doc = '"+strw+"'");

        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType(), sr);

        // root element
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());
        
        // first leaf:
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));

        String uri = sr.getAttributeNamespace(0);
        if (!URL_DEF.equals(uri)) {
            fail("Expected attribute 'attr' to have NS '"+URL_DEF+"', was "+valueDesc(uri)+"; input = '"+strw+"'");
        }
        assertEquals(ATTR_VALUE, sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // empty leaf
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertNull("Element <leaf/> should not be in a namespace", sr.getNamespaceURI());
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertNull(sr.getNamespaceURI());
        
        // third leaf
        assertTokenType(START_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr2", sr.getAttributeLocalName(0));
        String nsURI = sr.getAttributeNamespace(0);
        if (nsURI != null) {
            fail("Attribute 'attr2' should not belong to a namespace; reported to belong to '"+nsURI+"'");
        }
        assertEquals(ATTR_VALUE2, sr.getAttributeValue(0));

        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // closing root element
        assertTokenType(END_ELEMENT, sr.next(), sr);
        assertEquals("test", sr.getLocalName());
        assertEquals(URL_DEF, sr.getNamespaceURI());

        assertTokenType(END_DOCUMENT, sr.next(), sr);
    }
}
