package com.fsck.k9.ical;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;

public class ICalPart {
    private final Part underlyingPart;

    public ICalPart(Part part) throws MessagingException {
        this.underlyingPart = part;
    }

    public Part getPart() {
        return this.underlyingPart;
    }
}
