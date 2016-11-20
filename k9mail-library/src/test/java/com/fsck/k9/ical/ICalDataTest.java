package com.fsck.k9.ical;

import org.junit.Test;

import biweekly.ICalendar;
import biweekly.component.VEvent;

import static org.junit.Assert.assertEquals;

public class ICalDataTest {

    @Test
    public void ICalendarConstructor__createsICalData() {
        ICalendar iCalendar = new ICalendar();
        VEvent event = new VEvent();
        event.setSummary("EventSummary");
        iCalendar.addEvent(event);

        new ICalData(iCalendar);
    }

    @Test
    public void getSummary_returnsSummaryOfFirstEvent() {
        ICalendar iCalendar = new ICalendar();
        VEvent event = new VEvent();
        event.setSummary("EventSummary");
        iCalendar.addEvent(event);

        ICalData data = new ICalData(iCalendar);

        assertEquals("EventSummary", data.getSummary());
    }

    @Test
    public void getLocation_returnsLocationOfFirstEvent() {
        ICalendar iCalendar = new ICalendar();
        VEvent event = new VEvent();
        event.setLocation("Location");
        iCalendar.addEvent(event);

        ICalData data = new ICalData(iCalendar);

        assertEquals("Location", data.getLocation());
    }
}
