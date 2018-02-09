package org.codehaus.stax.test.evt;

import java.util.*;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

/**
 * Unit tests for testing that START_ELEMENT event object behaves
 * as expected
 *
 * @author Tatu Saloranta
 */
public class TestStartElem
    extends BaseEventTest
{
    /**
     * Simple tests to ensure that the namespace declarations are properly
     * parsed and accessible via {@link StartElement}.
     */
    public void testStartElemNs()
        throws XMLStreamException
    {
        String XML = "<root xmlns='http://my' xmlns:a='ns:attrs' "
            +"attr1='value1' a:attr2='value2'"
            +"/>";

        XMLEventReader er = getReader(XML, true, false);
        assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
        XMLEvent evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt.getEventType());
        // Ok, got the start element... is it ok?
        assertTrue(evt.isStartElement());
        StartElement se = evt.asStartElement();

        NamespaceContext nsCtxt = se.getNamespaceContext();

        assertNotNull("StartElement.getNamespaceContext() should never return null", nsCtxt);
        // First, ones we shouldn't get:
        assertNull(nsCtxt.getPrefix("a"));
        assertNull(nsCtxt.getPrefix("http://foobar"));
        assertNull(nsCtxt.getNamespaceURI("b"));
        assertNull(nsCtxt.getNamespaceURI("http://my"));

        {
            Iterator it = nsCtxt.getPrefixes("http://foobar");
            // Specs don't specify if we should get null, or empty iterator
            assertTrue((it == null) || !it.hasNext());
            it = nsCtxt.getPrefixes("a");
            assertTrue((it == null) || !it.hasNext());
        }
        // Then ones we should:

        assertEquals("a", nsCtxt.getPrefix("ns:attrs"));
        assertEquals("", nsCtxt.getPrefix("http://my"));
        assertEquals("http://my", nsCtxt.getNamespaceURI(""));
        assertEquals("ns:attrs", nsCtxt.getNamespaceURI("a"));

        // Plus, let's check the other namespace access:
        Iterator it = se.getNamespaces();
        assertEquals(2, countElements(it));

        assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
        assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
        assertFalse(er.hasNext());
    }

    public void testNestedStartElemNs()
        throws XMLStreamException
    {
        String XML = "<root><leaf xmlns='x' />"
            +"<branch xmlns:a='b' xmlns:x='url'>"
            +"<leaf xmlns:a='c' x:attr='value'/></branch>"
            +"</root>";

        XMLEventReader er = getReader(XML, true, false);
        assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
        XMLEvent evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt.getEventType());
        StartElement se = evt.asStartElement();

        // Let's first check that it has 1 declaration:
        assertEquals(0, countElements(se.getNamespaces()));
        NamespaceContext nsCtxt = se.getNamespaceContext();
        assertNotNull("StartElement.getNamespaceContext() should never return null", nsCtxt);
        assertNull(nsCtxt.getPrefix("a"));
        assertNull(nsCtxt.getNamespaceURI("b"));

        // then first leaf:
        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        se = evt.asStartElement();
        assertEquals("leaf", se.getName().getLocalPart());
        assertEquals(1, countElements(se.getNamespaces()));
        assertEquals("x", se.getName().getNamespaceURI());
        nsCtxt = se.getNamespaceContext();
        assertEquals("x", nsCtxt.getNamespaceURI(""));
        assertEquals("", nsCtxt.getPrefix("x"));

        assertTrue(er.nextEvent().isEndElement());

        // Ok, branch:
        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        se = evt.asStartElement();
        assertEquals("branch", se.getName().getLocalPart());
        assertEquals(2, countElements(se.getNamespaces()));
        nsCtxt = se.getNamespaceContext();
        assertEquals("a", nsCtxt.getPrefix("b"));
        assertEquals("b", nsCtxt.getNamespaceURI("a"));
        assertEquals("x", nsCtxt.getPrefix("url"));
        assertEquals("url", nsCtxt.getNamespaceURI("x"));

        // second leaf
        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        se = evt.asStartElement();
        nsCtxt = se.getNamespaceContext();
        assertEquals("leaf", se.getName().getLocalPart());
        // only one declared in this particular element
        assertEquals(1, countElements(se.getNamespaces()));
        // but should still show the other bound ns from parent
        nsCtxt = se.getNamespaceContext();
        assertEquals("a", nsCtxt.getPrefix("c"));
        assertEquals("c", nsCtxt.getNamespaceURI("a"));
        assertEquals("x", nsCtxt.getPrefix("url"));
        assertEquals("url", nsCtxt.getNamespaceURI("x"));
        // ok, but how about masking:
        assertNull(nsCtxt.getPrefix("b"));

        // Ok, fine... others we don't care about:
        assertTrue(er.nextEvent().isEndElement());
    }

    /**
     * Another unit test, one that checks a special case of namespace
     * enclosures and namespace context objects.
     */
    public void testNestedStartElemNs2()
        throws XMLStreamException
    {
        String XML = "<root><branch xmlns:a='url'><leaf /></branch></root>";

        XMLEventReader er = getReader(XML, true, false);
        assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());

        XMLEvent evt = er.nextEvent(); // root
        assertTokenType(START_ELEMENT, evt.getEventType());
        StartElement se = evt.asStartElement();
        assertEquals("root", se.getName().getLocalPart());
        assertEquals(0, countElements(se.getNamespaces()));

        evt = er.nextEvent(); // branch
        assertTokenType(START_ELEMENT, evt.getEventType());
        se = evt.asStartElement();
        assertEquals("branch", se.getName().getLocalPart());
        assertEquals(1, countElements(se.getNamespaces()));
        NamespaceContext nsCtxt = se.getNamespaceContext();
        assertNotNull("StartElement.getNamespaceContext() should never return null", nsCtxt);
        assertEquals("url", nsCtxt.getNamespaceURI("a"));
        assertEquals("a", nsCtxt.getPrefix("url"));

        evt = er.nextEvent(); // leaf
        assertTokenType(START_ELEMENT, evt.getEventType());
        se = evt.asStartElement();
        assertEquals("leaf", se.getName().getLocalPart());
        assertEquals(0, countElements(se.getNamespaces()));
        nsCtxt = se.getNamespaceContext();
        assertEquals("url", nsCtxt.getNamespaceURI("a"));
        assertEquals("a", nsCtxt.getPrefix("url"));

        assertTrue(er.nextEvent().isEndElement()); // /leaf
        assertTrue(er.nextEvent().isEndElement()); // /branch
        assertTrue(er.nextEvent().isEndElement()); // /root
    }


    /**
     * Test to check that attributes can be accessed normally via
     * {@link StartElement} instances.
     */
    public void testStartElemAttrs()
        throws XMLStreamException
    {
        String XML = "<root xmlns:a='ns:attrs' "
            +"attr1='value1' a:attr2='value2'"
            +"><leaf xmlns='url:ns2' attr='x' /></root>";

        XMLEventReader er = getReader(XML, true, false);
        assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());

        XMLEvent evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt.getEventType());
        StartElement se = evt.asStartElement();

	assertAttr(se, "", "attr1", "value1");
	assertAttr(se, "ns:attrs", "attr2", "value2");
	assertAttr(se, "", "attr2", null);
	assertAttr(se, "ns:attrs", "attr1", null);

        evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt.getEventType());
        se = evt.asStartElement();

	// One we should find (note: def. ns is not used!)
	assertAttr(se, "", "attr", "x");

	// and then ones that aren't there...
	assertAttr(se, "url:ns2", "attr", null);
	assertAttr(se, "url:ns2", "x", null);
	assertAttr(se, "ns:foo", "foobar", null);
	assertAttr(se, "", "attr1", null);

        assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
        assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
        assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
    }

    /*
    /////////////////////////////////////////////////
    // Internal methods:
    /////////////////////////////////////////////////
     */

    private int countElements(Iterator it) {
        int count = 0;
        if (it != null) {
            while (it.hasNext()) {
                ++count;
                it.next();
            }
        }
        return count;
    }

    private void assertAttr(StartElement se, String nsURI, String localName,
			    String expValue)
    {
	QName qn = new QName(nsURI, localName);
	Attribute attr = se.getAttributeByName(qn);

	if (expValue == null) {
	    assertNull("Should not find attribute '"+qn+"'", attr);
	} else {
	    assertNotNull("Should find attribute '"+qn+"' but got null", attr);
	    assertEquals("Attribute '"+qn+"' has unexpected value",
			 expValue, attr.getValue());
	}
    }

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coalesce);
        setSupportDTD(f, true);
        setValidating(f, false);
        return constructEventReader(f, contents);
    }
}
