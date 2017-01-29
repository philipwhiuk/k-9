package com.fsck.k9.ical;

import android.support.annotation.NonNull;

import com.fsck.k9.mail.Part;

public class ICalPart {
    private final Part underlyingPart;

    public ICalPart(@NonNull Part part) {
        this.underlyingPart = part;
    }

    public Part getPart() {
        return this.underlyingPart;
    }
}
