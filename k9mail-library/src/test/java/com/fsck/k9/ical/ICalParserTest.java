package com.fsck.k9.ical;


import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mailstore.BinaryMemoryBody;

import org.apache.james.mime4j.util.MimeUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class ICalParserTest {

    private byte[] iCalByteData;

    @Before
    public void before() throws IOException {
        ShadowLog.stream = System.out;
        createSampleICal();
    }

    private void createSampleICal() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ICalendar iCalendar = new ICalendar();
        VEvent event = new VEvent();
        iCalendar.addEvent(event);
        Biweekly.write(iCalendar).go(baos);
        iCalByteData = baos.toByteArray();
    }

    @Test
    public void parse_returnsEmptyArray_whenNoICalendarExists() throws MessagingException {
        Part underlyingPart = new MimeBodyPart(new BinaryMemoryBody(new byte[]{}, MimeUtil.ENC_8BIT));
        ICalPart part = new ICalPart(underlyingPart);
        ICalData[] data = ICalParser.parse(part);
        assertEquals(0, data.length);
    }

    @Test
    public void parse_returnsArrayWithData_whenICalendarExist() throws MessagingException {
        Part underlyingPart = new MimeBodyPart(new BinaryMemoryBody(iCalByteData, MimeUtil.ENC_8BIT));
        ICalPart part = new ICalPart(underlyingPart);
        ICalData[] data = ICalParser.parse(part);
        assertEquals(1, data.length);
    }
}