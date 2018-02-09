package org.codehaus.stax.test.evt;

import java.util.NoSuchElementException;

import javax.xml.stream.*;
import javax.xml.stream.events.*;

import java.util.*;

/**
 * Class that contains simple tests for making sure that event objects
 * created by the {@link XMLEventFactory} have expected properties.
 *
 * @author Tatu Saloranta
 */
public class TestEventReader
    extends BaseEventTest
{
    public void testSimpleValid()
        throws XMLStreamException
    {
        /* Whether prolog/epilog white space is reported is not defined
         * by StAX specs, thus, let's not add any
         */
        String XML = "<?xml version='1.0' ?>"
            +"<!DOCTYPE root [  ]>"
            +"<root attr='123'><!-- comment -->\n"
            +"</root>";

        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(DTD, er.nextEvent().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(COMMENT, er.nextEvent().getEventType());
            // for fun, let's just use next() instead of nextEvent()
            XMLEvent evt = (XMLEvent) er.next();
            assertTokenType(CHARACTERS, evt.getEventType());
            assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
            assertFalse(er.hasNext());
            er.close();
        }
    }

    public void testInvalidUsage()
        throws XMLStreamException
    {
        String XML = "<?xml version='1.0' ?><root />";
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);

            XMLEvent evt = er.nextEvent();

            // Let's try removal:
            String msg = null;
            try {
                er.remove();
                msg = "Was expecting UnsupportedOperationException for XMLEventReader.remove()";
            } catch (UnsupportedOperationException e) {
                ; // good
            } catch (Throwable t) {
                msg = "Was expecting UnsupportedOperationException for XMLEventReader.remove(); instead got: "+t;
            }
            if (msg != null) {
                fail(msg);
            }
        }
    }

    /**
     * Test that checks that entity objects are properly returned in
     * non-expanding mode
     */
    public void testNonExpandingEntities()
        throws XMLStreamException
    {
        /* Let's test all entity types
         */
        String XML = "<?xml version='1.0' ?>"
            +"<!DOCTYPE root [\n"
            +"<!ENTITY intEnt 'internal'>\n"
            +"<!ENTITY extParsedEnt SYSTEM 'url:dummy'>\n"
            +"<!NOTATION notation PUBLIC 'notation-public-id'>\n"
            // Hmmh: can't test this, but let's declare it anyway
            +"<!ENTITY extUnparsedEnt SYSTEM 'url:dummy2' NDATA notation>\n"
            +"]>"
            //+"<root>&intEnt;&extParsedEnt;&extUnparsedEnt;</root>"
            +"<root>&intEnt;&extParsedEnt;</root>"
            ;

        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            // false -> do not expand entities
            XMLEventReader er = getReader(XML, ns, coal, false);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(DTD, er.nextEvent().getEventType());
            XMLEvent evt = er.nextEvent();
            assertTrue(evt.isStartElement());

            evt = er.nextEvent();
            assertTokenType(ENTITY_REFERENCE, evt.getEventType());
            EntityReference ref = (EntityReference) evt;
            assertNotNull(ref);
            assertTrue(ref.isEntityReference());
            assertEquals("intEnt", ref.getName());
            EntityDeclaration ed = ref.getDeclaration();
            assertNotNull("Declaration of internal entity 'intEnt' should not be null", ed);
            assertEquals("intEnt", ed.getName());
            assertEquals("internal", ed.getReplacementText());
            assertNullOrEmpty(ed.getNotationName());
            assertNullOrEmpty(ed.getPublicId());
            assertNullOrEmpty(ed.getSystemId());

            evt = er.nextEvent();
            assertTokenType(ENTITY_REFERENCE, evt.getEventType());
            ref = (EntityReference) evt;
            assertNotNull(ref);
            assertTrue(ref.isEntityReference());
            assertEquals("extParsedEnt", ref.getName());
            ed = ref.getDeclaration();
            assertNotNull("Declaration of external entity 'extParsedEnt' should not be null", ed);
            assertEquals("extParsedEnt", ed.getName());
            assertNullOrEmpty(ed.getNotationName());
            assertNullOrEmpty(ed.getPublicId());
            assertEquals("url:dummy", ed.getSystemId());

            /*
            evt = er.nextEvent();
            assertTokenType(ENTITY_REFERENCE, evt.getEventType());
            ref = (EntityReference) evt;
            assertEquals("extUnparsedEnt", ref.getName());
            assertNotNull(ref);
            assertTrue(ref.isEntityReference());
            ed = ref.getDeclaration();
            assertNotNull(ed);
            assertEquals("notation", ed.getNotationName());
            */

            evt = er.nextEvent();
            assertTrue(evt.isEndElement());
            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
            assertFalse(er.hasNext());
        }
    }

    /**
     * This unit test checks that a DTD event that results from parsing
     * a valid document, to the degree it is done without having to
     * validate anything
     */
    public void testValidDtdEvent()
        throws XMLStreamException
    {
        String XML = "<?xml version='1.0' ?>"
            +"<!DOCTYPE root [\n"
            +"<!ENTITY intEnt 'internal'>\n"
            +"<!ENTITY extParsedEnt SYSTEM 'url:dummy'>\n"
            +"<!NOTATION notation PUBLIC 'notation-public-id'>\n"
            +"<!NOTATION notation2 SYSTEM 'url:dummy'>\n"
            +"<!ENTITY extUnparsedEnt SYSTEM 'url:dummy2' NDATA notation>\n"
            +"]>"
            +"<root />"
            ;
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            XMLEvent evt = er.nextEvent();
            assertTokenType(DTD, evt.getEventType());
            DTD dtd = (DTD) evt;
            /* isXxx() methods and writability are tested by a different
             * unit test (in TestEventTypes()); here let's just check for
             * entities and notations
             */
            List entities = dtd.getEntities();
            assertNotNull("Entity list for a DTD declaration with entities should not be null", entities);
            assertEquals(3, entities.size());

            // Let's also verify they are all of right type...
            testListElems(entities, EntityDeclaration.class);

            List notations = dtd.getNotations();

            // Let's also verify they are all of right type...
            testListElems(notations, NotationDeclaration.class);

            assertNotNull("Notation list for a DTD declaration with notations should not be null", entities);
            assertNotNull(notations);
            assertEquals(2, notations.size());
        }
    }

    /**
     * The main purpose of this test is to ensure that an exception
     * is thrown at the end.
     */
    public void testIterationEndException()
        throws XMLStreamException
    {
        String XML = "<root />";

        for (int i = 0; i < 4; ++i) {
            boolean coal = (i & 1) != 0;
            boolean checkHasNext = (i & 2) != 0;
            XMLEventReader er = getReader(XML, true, coal);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());

            if (checkHasNext) {
                assertFalse(er.hasNext());
            }

            XMLEvent ev = null;
            try {
                ev = er.nextEvent();
            } catch (NoSuchElementException nex) {
                continue; // good
            } catch (Throwable t) {
                fail("Expected a NoSuchElementException after iterating through the document; got "+t);
            }

            // Shouldn't get this far...
            fail("Expected a NoSuchElementException after iterating through the document; got event: "+ev);
        }
    }

    public void testNextTagOk()
        throws XMLStreamException
    {
        String XML = "<root>\n"
            +"<branch>   <leaf>  </leaf></branch>"
            +"</root>";

        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());

            assertTokenType(START_ELEMENT, er.nextTag().getEventType());
            assertTokenType(START_ELEMENT, er.nextTag().getEventType());
            /* Ok, let's mix in bit of peeking to ensure reader won't
             * be confused too badly...
             */
            // This should be space between <branch> and <leaf>...
            assertTokenType(CHARACTERS, er.peek().getEventType());

            // And then the leaf
            assertTokenType(START_ELEMENT, er.nextTag().getEventType());

            assertTokenType(END_ELEMENT, er.nextTag().getEventType());
            assertTokenType(END_ELEMENT, er.nextTag().getEventType());
            assertTokenType(END_ELEMENT, er.nextTag().getEventType());

            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
            assertFalse(er.hasNext());
        }
    }

    public void testNextTagInvalid()
        throws XMLStreamException
    {
        String XML = "<root>   non-empty<leaf /></root>";
        String XML2 = "<root><leaf></leaf>text   </root>";

        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            String msg = null;
            try {
                XMLEvent evt = er.nextTag();
                msg = "Expected a XMLStreamException when trying to call XMLEventReader.nextTag() on non-empty CHARACTERS";
            } catch (XMLStreamException sex) {
                // fine!
            } catch (Throwable t) {
                msg = "Expected a XMLStreamException when trying to call XMLEventReader.nextTag() on non-empty CHARACTERS; got ("+t.getClass()+"): "+t;
            }
            if (msg != null) {
                fail(msg);
            }
            er.close();

            /* any other easily failing cases? Maybe if we are on top of
             * END_ELEMENT, and will hit another one?
             */
            er = getReader(XML2, ns, coal);
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(END_ELEMENT, er.nextEvent().getEventType());

            try {
                XMLEvent evt = er.nextTag();
                msg = "Expected a XMLStreamException when trying to call XMLEventReader.nextTag() on END_ELEMENT and hitting non-ws text; got event "+tokenTypeDesc(evt);
            } catch (XMLStreamException sex) {
                msg = null; // fine!
            } catch (Throwable t) {
                msg = "Expected a XMLStreamException when trying to call XMLEventReader.nextTag() on END_ELEMENT and hitting non-ws text; got: "+t;
            }
            if (msg != null) {
                fail(msg);
            }
            er.close();
        }
    }

    public void testSkip()
        throws XMLStreamException
    {
        String XML = "<?xml version='1.0' ?><!DOCTYPE root><root>\n"
            +"<branch>   <leaf>  </leaf></branch><!-- comment -->"
            +"</root>";

        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            XMLEvent ev;

            assertTokenType(START_DOCUMENT, er.peek().getEventType());
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(DTD, er.peek().getEventType());
            assertTokenType(DTD, er.nextEvent().getEventType());
            assertTokenType(START_ELEMENT, er.peek().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());

            assertTokenType(CHARACTERS, er.peek().getEventType());
            assertTokenType(CHARACTERS, er.nextEvent().getEventType());

            // branch
            assertTokenType(START_ELEMENT, er.peek().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(CHARACTERS, er.peek().getEventType());
            assertTokenType(CHARACTERS, er.nextEvent().getEventType());

            // leaf
            assertTokenType(START_ELEMENT, er.peek().getEventType());
            assertTokenType(START_ELEMENT, er.nextEvent().getEventType());
            assertTokenType(CHARACTERS, er.peek().getEventType());
            assertTokenType(CHARACTERS, er.nextEvent().getEventType());
            assertTokenType(END_ELEMENT, er.peek().getEventType());
            assertTokenType(END_ELEMENT, er.nextEvent().getEventType());

            assertTokenType(END_ELEMENT, er.peek().getEventType());
            assertTokenType(END_ELEMENT, er.nextEvent().getEventType());

            assertTokenType(COMMENT, er.peek().getEventType());
            assertTokenType(COMMENT, er.nextEvent().getEventType());
            assertTokenType(END_ELEMENT, er.peek().getEventType());
            assertTokenType(END_ELEMENT, er.nextEvent().getEventType());

            assertTokenType(END_DOCUMENT, er.peek().getEventType());
            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
            assertFalse(er.hasNext());
        }
    }
    
    /**
     * This test was inspired by an actual bug in one of implementations:
     * initial state was not properly set if nextTag() was called (instead
     * of nextEvent()), and subsequent peek() failed.
     */
    public void testPeek()
        throws XMLStreamException
    {
        String XML = "<root>text</root>";

        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());

            XMLEvent tag = er.nextTag();
            assertTokenType(START_ELEMENT, tag.getEventType());

            // Now, peek() should produce text..
            XMLEvent text = er.peek();
            assertTokenType(CHARACTERS, text.getEventType());
            Characters chars = text.asCharacters();
            assertNotNull(chars);
            assertEquals("text", chars.getData());
            
            // and need nextEvent() to get rid of it, too:
            text = er.nextEvent();
            // Let's verify it again:
            assertTokenType(CHARACTERS, text.getEventType());
            chars = text.asCharacters();
            assertNotNull(chars);
            assertEquals("text", chars.getData());
            assertTokenType(END_ELEMENT, er.nextTag().getEventType());
            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());

            // And at the end, peek() should return null
            assertNull(er.peek());
        }
    }

    public void testElementText()
        throws XMLStreamException
    {
        String TEXT1 = "some\ntest";
        String TEXT2 = "inside CDATA too!";

        String XML = "<root>"+TEXT1+"<![CDATA["+TEXT2+"]]></root>";

        // First, let's see how things work without peeking:
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            XMLEvent elem = er.nextEvent();
            assertTokenType(START_ELEMENT, elem.getEventType());

            try {
                assertEquals(TEXT1+TEXT2, er.getElementText());
            } catch (XMLStreamException sex) {
                fail("Failed on XMLEventReader.getElementText(): "+sex);
            }

            /* 06-Jan-2006, TSa: I'm really not sure whether to expect
             *   END_ELEMENT, or END_DOCUMENT here... maybe the latter
             *   makes more sense? For now let's accept both, however.
             */
            elem = er.nextEvent();
            if (elem.getEventType() != END_DOCUMENT
                && elem.getEventType() != END_ELEMENT) {
                fail("Expected END_DOCUMENT or END_ELEMENT, got "+tokenTypeDesc(elem.getEventType()));
            }
        }

        // and then with peeking:
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            XMLEvent elem = er.nextEvent();
            assertTokenType(START_ELEMENT, elem.getEventType());

            XMLEvent peeked = er.peek();
            assertTokenType(CHARACTERS, peeked.getEventType());

            assertEquals(TEXT1+TEXT2, er.getElementText());

            /* 06-Jan-2006, TSa: I'm really not sure whether to expect
             *   END_ELEMENT, or END_DOCUMENT here... maybe the latter
             *   makes more sense? For now let's accept both, however.
             */
            elem = er.nextEvent();
            if (elem.getEventType() != END_DOCUMENT
                && elem.getEventType() != END_ELEMENT) {
                fail("Expected END_DOCUMENT or END_ELEMENT, got "+tokenTypeDesc(elem.getEventType()));
            }
        }
    }

    /*
    /////////////////////////////////////////////////
    // Internal methods:
    /////////////////////////////////////////////////
     */

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce)
        throws XMLStreamException
    {
        return getReader(contents, nsAware, coalesce, true);
    }

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce, boolean expandEnt)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coalesce);
        setSupportDTD(f, true);
        setValidating(f, false);
        setReplaceEntities(f, expandEnt);
        return constructEventReader(f, contents);
    }

    private void testListElems(List l, Class expType)
    {
        Iterator it = l.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            assertNotNull(o);
            assertTrue(expType.isAssignableFrom(o.getClass()));
        }
    }
}
