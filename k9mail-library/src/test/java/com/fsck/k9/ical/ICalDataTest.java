package com.fsck.k9.ical;

import org.junit.Test;

import biweekly.ICalendar;
import biweekly.component.VEvent;

import static org.junit.Assert.assertEquals;

public class ICalDataTest {

    @Test
    public void ICalendarConstructor__createsICalData() {
        VEvent event = new VEvent();
        event.setSummary("EventSummary");

        new ICalData(null, event);
    }

    @Test
    public void getSummary_returnsSummaryOfFirstEvent() {
        VEvent event = new VEvent();
        event.setSummary("EventSummary");

        ICalData data = new ICalData(null, event);

        assertEquals("EventSummary", data.getSummary());
    }

    @Test
    public void getLocation_returnsLocationOfFirstEvent() {
        VEvent event = new VEvent();
        event.setLocation("Location");

        ICalData data = new ICalData(null, event);

        assertEquals("Location", data.getLocation());
    }
}
