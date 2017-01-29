package com.fsck.k9.ical;

import com.fsck.k9.mail.internet.MessageExtractor;

import java.util.ArrayList;
import java.util.List;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;

public class ICalParser {
    public static final String MIME_TYPE = "text/calendar";

    public static ICalData[] parse(ICalPart part) {
        String iCalText = MessageExtractor.getTextFromPart(part.getPart());

        //TODO: Handle more than one entry
        List<ICalendar> iCalendars = Biweekly.parse(iCalText).all();

        List<ICalData> iCalData = new ArrayList<>();

        for (ICalendar iCalendar : iCalendars) {
            for (VEvent event: iCalendar.getEvents())
            iCalData.add(new ICalData(iCalendar.getMethod(), event));
        }

        return iCalData.toArray(new ICalData[iCalData.size()]);
    }
}
